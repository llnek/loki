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

  czlab.loki.game.engine

  (:require [czlab.xlib.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.loki.event.core]
        [czlab.xlib.core]
        [czlab.xlib.str])

  (:import [czlab.loki.core Engine Game Arena Session]
           [czlab.loki.event Events]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- dummy2 [a b] nil)
(defn- dummy1 [a] nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn engine<>
  ""
  [^Arena arenaObj
   {:keys [onRestart
           onStart
           onStop
           onUpdate
           onDispose]
    :or {onRestart dummy2
         onStart dummy2
         onStop dummy1
         onUpdate dummy2
         onDispose dummy1}
    :as impl}]
  {:pre [(some? arenaObj)]}
  (let
    [state (atom {})]
    (reify Engine
      (init [_ players]
        (let [sessionids
              (preduce<map>
                #(let [^Session s %2]
                   (assoc! %1 (.id s) s))
                players)
              playerids
              (preduce<map>
                #(let [^Session s %2]
                  (assoc! %1
                          (.. s player id)
                          (.number s)))
                players)]
          (swap! state
                 assoc
                 :playerids playerids
                 :sessions sessionids
                 :players players )))
      (ready [this room]
        (log/debug "engine#ready called")
        (swap! state assoc :room room)
        (->> (:playerids @state)
             (eventObj<> Events/LOCAL
                         Events/START)
             (.send (.container this))))
      (restart [_ arg]
        (log/debug "engine#restart called")
        (onRestart @state arg))
      (start [_ arg]
        (log/debug "engine#start called")
        (onStart @state arg))
      (startRound [this arg]
        (->> {:round (:round arg)}
             (eventObj<> Events/LOCAL
                         Events/START_ROUND)
             (.send (.container this))))
      (endRound [this arg]
        (->> {:round (:round arg)}
             (eventObj<> Events/LOCAL
                         Events/END_ROUND)
             (.send (.container this))))
      (stop [this]
        (->> (eventObj<> Events/LOCAL Events/STOP nil)
             (.send (.container this)))
        (onStop @state))
      (update [this evt]
        (.onEvent arenaObj
                  ^Session (:context evt)
                  (dissoc :context evt)))
      (dispose [_]
        (onDispose @state))
      (state [_] @state)
      (container [_] (:room @state)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


