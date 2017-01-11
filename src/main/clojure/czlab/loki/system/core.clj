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

  (:require [czlab.xlib.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.convoy.netty.core]
        [czlab.flux.wflow.core]
        [czlab.xlib.format]
        [czlab.xlib.core]
        [czlab.xlib.io]
        [czlab.xlib.str]
        [czlab.loki.system.util]
        [czlab.loki.event.core]
        [czlab.loki.game.reqs])

  (:import [czlab.flux.wflow WorkStream Job]
           [czlab.wabbit.io WSockEvent]
           [czlab.loki.event Events]
           [czlab.loki.core Session]
           [czlab.xlib XData]
           [java.io File]
           [io.netty.channel Channel]
           [czlab.wabbit.server Container]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lokiOnEvent
  "Handle job directly"
  [^Job job]

  (let [^WSockEvent ws (.event job)
        ^Channel ch (.socket ws)
        {:keys [type code] :as req}
        (->> {:socket ch
              :source (.source ws)}
             (decodeEvent (.. ws
                              body stringify)))]
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
  ^WorkStream
  []
  (workStream<>
    (script<>
      #(lokiOnEvent %2))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


