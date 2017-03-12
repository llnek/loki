;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.system.core

  (:require [czlab.basal.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.convoy.nettio.core]
        [czlab.flux.wflow.core]
        [czlab.basal.format]
        [czlab.basal.core]
        [czlab.basal.io]
        [czlab.basal.str]
        [czlab.loki.system.util]
        [czlab.loki.event.core]
        [czlab.loki.game.reqs])

  (:import [czlab.flux.wflow Workstream Job]
           [czlab.wabbit.plugs.io WsockMsg]
           [czlab.loki.event Events]
           [czlab.loki.core Session]
           [czlab.jasal XData]
           [java.io File]
           [io.netty.channel Channel]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- lokiOnEvent "" [^Job job]

  (let [^WsockMsg ws (.origin job)
        ch (.socket ws)
        {:keys [type code] :as req}
        (->> {:socket ch
              :source (.source ws)}
             (decodeEvent (.. ws
                              body strit)))]
    (cond
      (and (== type Events/UNIT)
           (== code Events/PLAYGAME_REQ))
      (doPlayReq req)

      (and (== type Events/UNIT)
           (== code Events/JOINGAME_REQ))
      (doJoinReq req)

      :else
      (if-some [^Session ss (getAKey ch PSSN)]
        (->> (assoc req :context ss)
             (.receive (.room ss)))
        (log/error "no session attached to socket")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lokiInit
  "Initialize loki"
  ([] (lokiInit nil))
  ([arg]
   {:pre [(or (nil? arg)
              (map? arg))]}
   (log/info "loki config= %s" arg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lokiHandler
  "Wrap handler as a workflow"
  ^Workstream [] (workstream<> lokiOnEvent))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


