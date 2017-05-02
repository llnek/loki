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

  (:require [czlab.loki.xpis :as loki :refer :all]
            [czlab.basal.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.basal.format]
        [czlab.basal.core]
        [czlab.basal.io]
        [czlab.basal.str]
        [czlab.loki.util]
        [czlab.convoy.core]
        [czlab.wabbit.xpis]
        [czlab.loki.net.core]
        [czlab.loki.game.reqs])

  (:import [java.io File]
           [czlab.jasal XData Receivable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lokiHandler "" [evt]
  (let [s (.strit ^XData (:body evt))
        p (get-pluglet evt)
        ch (:socket evt)
        {:keys [type code] :as req}
        (decodeEvent s {:socket ch
                        :source p})]
    (cond
      (and (isPrivate? req)
           (isCode? ::loki/playgame-req req))
      (doPlayReq req)

      (and (isPrivate? req)
           (isCode? ::loki/joingame-req req))
      (doJoinReq req)

      :else
      (let [{:keys [room session]}
            (get-socket-attr ch RMSN)]
        (if (and session room)
          (->> (assoc req :context session )
               (.receive ^Receivable room))
          (log/error "no session attached to socket"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


