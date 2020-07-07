(ns atomist.main-t
  (:require [cljs.test :refer-macros [deftest is are use-fixtures async run-tests]]
            [cljs.core.async :refer-macros [go] :refer [<!]]
            [atomist.main :as main]))

(defn- check-message [s]
  ((main/check-commit-message #(go %))
   {:data {:PullRequest [{:number 1
                          :head {:message s}}]}
    :ref {:owner "org" :repo "repo"}}))

(deftest check-commit-message-test
  (async
   done
    (go
     (let [response (<! (check-message "bad first line"))]
       (are [x y] (= x y)
                  "failure" (:checkrun/conclusion response)
                  "The commit message should begin with a capital letter." (-> response :checkrun/output :summary))
       (done)))))

(deftest check-long-first-line
  (async
   done
    (go
     (let [response (<! (check-message (apply str (repeat 80 'A))))]
       (are [x y] (= x y)
                  "failure" (:checkrun/conclusion response)
                  "The commit message subject is over 50 characters." (-> response :checkrun/output :summary))
       (done)))))

(deftest check-ending-with-a-period
  (async
   done
    (go
     (let [response (<! (check-message "This messages ends with a period."))]
       (are [x y] (= x y)
                  "failure" (:checkrun/conclusion response)
                  "The first line of the commit message is the subject, and should not end with a period." (-> response :checkrun/output :summary))
       (done)))))

(deftest check-imperative-tense
  (async
   done
    (go
     (let [response (<! (check-message "A good start\n\nBut then we fixed and updated and changed"))]
       (are [x y] (= x y)
                  "failure" (:checkrun/conclusion response)
                  "The commit message should be written in the imperative mood, like a command, so 'Add' instead of 'Added'." (-> response :checkrun/output :summary))
       (done)))))
