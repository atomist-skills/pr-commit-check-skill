(ns atomist.main
  (:require [atomist.api :as api]
            [cljs.core.async :refer [<!]]
            [goog.string.format]
            [clojure.data]
            [goog.string :as gstring]
            [atomist.cljs-log :as log]
            [atomist.github :as github]
            [cljs.reader :refer [read-string]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn only-process-pr-branch-updates
  [handler]
  (fn [request]
    (go
      (api/trace "only-process-new-pr-pushes")
      (let [{:keys [number head action]} (-> request
                                             :data
                                             :PullRequest
                                             first)
            commit-message (:message head)]
        (if (and (= "OnPullRequest" (:operation request))
                 (#{"opened" "synchronize"} action))
          (<! (handler
               (assoc request :commit-message commit-message :pr-number number)))
          (<! (api/finish (assoc request
                                 :status-message
                                 (gstring/format "skip operation %s action %s"
                                                 (:operation request)
                                                 action))
                          :visibility
                          :hidden)))))))

(defn send-pr-comment
  [handler]
  (fn [request]
    (go (api/trace "send-pr-comment")
        (when (= "failure"
                 (-> request
                     :checkrun/conclusion))
          (log/debugf
           "post-pr-comment status %d"
           (:status (<! (github/post-pr-comment
                         (merge (:ref request) {:token (:token request)})
                         (:pr-number request)
                         (-> request
                             :checkrun/output
                             :summary))))))
        (<! (handler request)))))

(comment
 (def rules
   ["[\"^[a-z]\" \"The commit message should begin with a capital letter.\"]"
    "[\"^[^\\n]{51}\" \"The commit message subject is over 50 characters.\"]"
    "[\"^[^\\n]*\\\\.(\\n|$)\" \"The first line of the commit message is the subject, and should not end with a period.\"]"
    "[\"^[Aa]dded|[Ff]ixed|[Uu]pdated|[Cc]hanged\" \"The commit message should be written in the imperative mood, like a command, so 'Add' instead of 'Added'.\"]"])

 (def message "## Violations\n%s\n\nsee [How to write a Git Commit Message](https://chris.beams.io/posts/git-commit/#seven-rules)\n\n"))

(defn- read-rule [s]
  (try
    (read-string s)
    (catch :default ex
      (log/error ex)
      (log/warnf "error parsing %s" s))))

(defn check-commit-message
  "check the commit-message"
  [handler]
  (fn [request]
    (go
      (api/trace "check-commit-message")
      (try
        (let [{:keys [owner repo]} (:ref request)]
          (log/debugf "check the commit-message `%s`" (:commit-message request))
          (if-let [violations (->> (:rules request)
                                   (map read-rule)
                                   (map
                                    (fn [[re violation]]
                                      (when re
                                        (and (re-find (re-pattern re) (:commit-message request))
                                             violation))))
                                   (filter identity)
                                   (seq))]
            (<!
             (handler
              (assoc
               request
               :status-message (gstring/format
                                "Check HEAD commit message on #%d - %s/%s"
                                (:pr-number request)
                                owner
                                repo)
               :checkrun/conclusion "failure"
               :checkrun/output
               {:title "Commit Message Check Failure"
                :summary
                (gstring/format
                 (:template request)
                 (apply str (interpose "\n" (map #(str "* " %) violations))))})))
            (<!
             (handler
              (assoc request
                     :status-message (gstring/format
                                      "Check HEAD commit message on #%d - %s/%s"
                                      (:pr-number request)
                                      owner
                                      repo)
                     :checkrun/conclusion "success"
                     :checkrun/output {:title "Commit Message Check Success"
                                       :summary "message passed all checks"})))))
        (catch :default ex
          (<! (handler (assoc request :status-message (str ex)))))))))

(def handle-pr-or-push
  (-> (api/finished)
      (send-pr-comment)
      (check-commit-message)
      (api/with-github-check-run :name "pr-commit-check-skill")
      (api/clone-ref)
      (api/extract-github-token)
      (api/create-ref-from-event)
      (api/add-skill-config)
      (only-process-pr-branch-updates)
      (api/log-event)
      (api/status :send-status :status-message)))

(defn ^:export handler
  [data callback]
  (api/make-request data
                    callback
                    (api/dispatch {:OnPullRequest handle-pr-or-push})))
