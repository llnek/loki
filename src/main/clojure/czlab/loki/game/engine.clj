;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.game.engine

  (:require [czlab.xlib.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.loki.event.core]
        [czlab.xlib.core]
        [czlab.xlib.str])

  (:import [czlab.loki.game Engine Game Arena]
           [czlab.loki.core Session]
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


