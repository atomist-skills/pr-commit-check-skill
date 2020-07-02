(ns atomist.main
  (:require [atomist.api :as api]
            [cljs.pprint :refer [pprint]]
            [cljs.core.async :refer [<!]]
            [goog.string.format]
            [clojure.data]
            [atomist.cljs-log :as log]
            [atomist.github])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn custom-middleware [handler]
  (fn [request]
    (go
      (log/info "do something useful here")
      (<! (handler request)))))

(defn ^:export handler
  [data sendreponse]
  (api/make-request
   data
   sendreponse
   (-> (api/finished :message "----> event handler finished")
       (custom-middleware)
       (api/log-event)
       (api/status))))
