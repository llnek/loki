;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.

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


