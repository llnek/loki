;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.core

  (:require [czlab.loki.xpis :as loki]
            [czlab.basal.log :as log]
            [clojure.java.io :as io]
            [czlab.basal.core :as c]
            [czlab.basal.io :as i]
            [czlab.basal.str :as s]
            [czlab.loki.util :as u]
            [czlab.convoy.core :as cc]
            [czlab.wabbit.xpis :as xp]
            [czlab.loki.net.core :as nc]
            [czlab.loki.game.reqs :as rs])

  (:import [java.io File]
           [czlab.jasal XData Receivable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lokiHandler "" [evt]
  (let [s (.strit ^XData (:body evt))
        p (xp/get-pluglet evt)
        ch (:socket evt)
        {:keys [type code] :as req}
        (nc/decodeEvent s {:socket ch
                           :source p})]
    (cond
      (and (nc/isPrivate? req)
           (nc/isCode? ::loki/playgame-req req))
      (rs/doPlayReq req)

      (and (nc/isPrivate? req)
           (nc/isCode? ::loki/joingame-req req))
      (rs/doJoinReq req)

      :else
      (let [{:keys [room session]}
            (cc/get-socket-attr ch u/RMSN)]
        (if (and session room)
          (->> (assoc req :context session )
               (.receive ^Receivable room))
          (log/error "no session attached to socket"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


