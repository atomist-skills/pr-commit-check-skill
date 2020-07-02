(ns atomist.main
  (:require [atomist.api :as api]
            [cljs.core.async :refer [<!]]
            [goog.string.format]
            [clojure.data]
            [goog.string :as gstring]
            [atomist.github])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn custom-middleware [handler]
  (fn [request]
    (go
      (api/trace "custom-middleware")
      (try
        (let [{:keys [token]} request
              {:keys [owner repo sha]} (:ref request)
              commit-message (or
                              (-> request :data :PullRequest first :mergeCommit :message)
                              (-> request :data :Push first :after :message)
                              "unknown")]
          (<! (handler (assoc request
                              :status-message (gstring/format "Operation %s - %s/%s - %s"
                                                              (-> request :extensions :operationName)
                                                              (-> request :ref :owner)
                                                              (-> request :ref :repo)
                                                              commit-message)))))
        (catch :default ex
          (<! (handler (assoc request :status-message (str ex)))))))))

(def handle-pr-or-push (-> (api/finished)
                           (custom-middleware)
                           (api/clone-ref)
                           (api/create-ref-from-event)
                           (api/add-skill-config)
                           (api/log-event)
                           (api/status :send-status :status-message)))

(defn ^:export handler
  [data callback]
  (api/make-request
   data
   callback
   (api/dispatch {:OnPullRequest handle-pr-or-push
                  :OnAnyPush handle-pr-or-push})))
