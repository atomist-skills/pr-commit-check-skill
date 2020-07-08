(ns atomist.main
  (:require [atomist.api :as api]
            [cljs.core.async :refer [<!]]
            [goog.string.format]
            [clojure.data]
            [goog.string :as gstring]
            [atomist.cljs-log :as log]
            [atomist.github :as github])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn only-process-new-pr-pushes
  [handler]
  (fn [request]
    (go (api/trace "only-process-new-pr-pushes")
      (let [commit-message (or (-> request :data :PullRequest first :head :message)
                               (-> request :data :Push first :after :message))
            pr-number (or (-> request :data :PullRequest first :number)
                          (-> request :data :Push first :after :pullRequests first :number))]
        (cond
          (and (= "OnPullRequest" (:operation request))
               (= "opened" (-> request :data :PullRequest first :action)))
          (<! (handler (assoc request
                         :commit-message commit-message
                         :pr-number pr-number)))
          (and (= "OnPush" (:operation request))
               (seq (-> request :data :Push first :after :pullRequests)))
          (<! (handler assoc request
                       :commit-message commit-message
                       :pr-number pr-number))
          :else
          (<! (api/finish request
                          :status-message (gstring/format "skip non-PR %s" (:operation request))
                          :visibility :hidden)))))))

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

(def rules
  [[#"^[a-z]" "The commit message should begin with a capital letter."]
   [#"^[^\n]{51}" "The commit message subject is over 50 characters."]
   [#"^[^\n]*\.(\n|$)"
    "The first line of the commit message is the subject, and should not end with a period."]
   [#"^[Aa]dded|[Ff]ixed|[Uu]pdated|[Cc]hanged"
    "The commit message should be written in the imperative mood, like a command, so 'Add' instead of 'Added'."]])

(defn check-commit-message
  "check the commit-message"
  [handler]
  (fn [request]
    (go
     (api/trace "check-commit-message")
     (try (let [{:keys [owner repo]} (:ref request)]
            (log/debugf "check the commit-message `%s`" (:commit-message request))
            (if-let [violations (->> rules
                                     (map (fn [[re violation]]
                                            (and (re-find re (:commit-message request))
                                                 violation)))
                                     (filter identity)
                                     (seq))]
              (<! (handler
                   (assoc request
                          :status-message
                          (gstring/format
                           "Check HEAD commit message on #%d - %s/%s"
                           (:pr-number request)
                           owner
                           repo)
                          :checkrun/conclusion "failure"
                          :checkrun/output
                          {:title "Contributor Commit Message Check",
                           :summary (apply str (interpose "\n" violations))})))
              (<! (handler (assoc request
                                  :status-message
                                  (gstring/format
                                   "Check HEAD commit message on #%d - %s/%s"
                                   (:pr-number request)
                                   owner
                                   repo)
                                  :checkrun/conclusion "success"
                                  :checkrun/output
                                  {:title "Contributor Commit Message Check",
                                   :summary "Good Commit message"})))))
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
      (only-process-new-pr-pushes)
      (api/log-event)
      (api/status :send-status :status-message)))

(defn ^:export handler
  [data callback]
  (api/make-request data
                    callback
                    (api/dispatch {:OnPullRequest handle-pr-or-push
                                   :OnPush handle-pr-or-push})))
