(ns atomist.main-t
  (:require [cljs.test :refer-macros
             [deftest is are use-fixtures async run-tests]]
            [cljs.core.async :refer-macros [go] :refer [<!]]
            [atomist.main :as main]
            [clojure.string :as s]))

(def rules
  ["[\"^[a-z]\" \"The commit message should begin with a capital letter.\"]"
   "[\"^[^\\n]{51}\" \"The commit message subject is over 50 characters.\"]"
   "[\"^[^\\n]*\\\\.(\\n|$)\" \"The first line of the commit message is the subject, and should not end with a period.\"]"
   "[\"^[Aa]dded|[Ff]ixed|[Uu]pdated|[Cc]hanged\" \"The commit message should be written in the imperative mood, like a command, so 'Add' instead of 'Added'.\"]"])

(def regexes (->> rules
                  (map (comp re-pattern first main/read-rule))
                  (into [])))

(def message "## Violations\n%s\n\nsee [How to write a Git Commit Message](https://chris.beams.io/posts/git-commit/#seven-rules)\n\n")

(defn- check-message
  [s]
  ((main/check-commit-message #(go %))
   {:commit-message s
    :pr-number 1
    :ref {:owner "org", :repo "repo"}
    :template message
    :rules rules}))

(deftest check-commit-message-test
  (async done
         (go (let [response (<! (check-message "bad first line"))]
               (are [x y]
                    (s/includes? y x)
                 "failure" (:checkrun/conclusion response)
                 "The commit message should begin with a capital letter."
                 (-> response
                     :checkrun/output
                     :summary))
               (done)))))

(deftest check-long-first-line
  (async done
         (go (let [response (<! (check-message (apply str (repeat 80 'A))))]
               (are [x y]
                    (s/includes? y x)
                 "failure" (:checkrun/conclusion response)
                 "The commit message subject is over 50 characters."
                 (-> response
                     :checkrun/output
                     :summary))
               (done)))))

(deftest check-ending-with-a-period
  (async
   done
   (go
     (let [response (<! (check-message "This messages ends with a period."))]
       (are
        [x y]
        (s/includes? y x)
         "failure" (:checkrun/conclusion response)
         "The first line of the commit message is the subject, and should not end with a period."
         (-> response
             :checkrun/output
             :summary))
       (done)))))

(deftest check-imperative-tense
  (async
   done
   (go
     (let [response
           (<! (check-message
                "A good start\n\nBut then we fixed and updated and changed"))]
       (are
        [x y]
        (s/includes? y x)
         "failure" (:checkrun/conclusion response)
         "The commit message should be written in the imperative mood, like a command, so 'Add' instead of 'Added'."
         (-> response
             :checkrun/output
             :summary))
       (done)))))

(deftest skip-prs-not-being-opened
  (async
   done
   (go (let [response (<! ((main/only-process-pr-branch-updates #(go %))
                           {:operation "OnPullRequest"
                            :correlation_id "corrid"
                            :api_version "1"
                            :data {:PullRequest [{:action "edited"}]}}))]
         (are [x y]
              (= x y)
           :hidden (-> response
                       :api/status
                       :visibility)
           "skip operation OnPullRequest action edited" (-> response
                                                            :status-message))
         (done)))))

(enable-console-print!)
(run-tests)