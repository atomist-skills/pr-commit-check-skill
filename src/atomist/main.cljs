(ns atomist.main
  (:require [atomist.api :as api]
            [cljs.core.async :refer [<!]]
            [goog.string.format]
            [clojure.data]
            [goog.string :as gstring]
            [atomist.github]
            [atomist.cljs-log :as log])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn custom-middleware [handler]
  (fn [request]
    (go
      (api/trace "custom-middleware")
      (try
        (let [{:keys [owner repo]} (:ref request)
              {:keys [number] :as pr} (-> request :data :PullRequest first)
              commit-message (-> pr :head :message)]
          ;; TODO check the commit-message here
          (log/debugf "check the commit-message `%s`" commit-message)
          ;; Note - the request contains a token (:token request) so we can call GitHub apis here
          (<! (handler (assoc request :status-message (gstring/format "Check HEAD commit message on #%d - %s/%s" number owner repo)))))
        (catch :default ex
          (<! (handler (assoc request :status-message (str ex)))))))))

(def handle-pr-or-push (-> (api/finished)
                           (custom-middleware)
                           (api/clone-ref)
                           (api/extract-github-token)
                           (api/create-ref-from-event)
                           (api/add-skill-config)
                           (api/log-event)
                           (api/status :send-status :status-message)))

(defn ^:export handler
  [data callback]
  (api/make-request
   data
   callback
   (api/dispatch {:OnPullRequest handle-pr-or-push})))
