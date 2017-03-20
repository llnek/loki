;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.game.arena

  (:require [czlab.basal.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.loki.net.core]
        [czlab.basal.format]
        [czlab.basal.core]
        [czlab.basal.str])

  (:import [czlab.loki.sys Session]
           [czlab.loki.game
            GameImpl
            GameMeta
            Arena
            GameRoom]
           [czlab.loki.net Events]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

(def ^:private _latch_mutex_ (Object.))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtStartBody [^GameImpl impl sessions]
  (preduce<map>
    #(let [^Session s %2
           sn (.number s)
           y (.player s)
           yid (.id y)
           g (.playerGist impl yid)]
       (assoc! %1
               (keyword yid)
               (merge {:session_number sn} g))) sessions))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn arena<>
  "" ^Arena [^GameRoom room ^GameImpl impl]

  (let [state (atom {:room room})
        latch (atom nil)]
    (reify Arena
      (init [_ sessions]
        (log/debug "arena#init() called")
        (swap! state
               assoc
               :sessions sessions
               :sids (preduce<map>
                       #(assoc! %1
                                (.id ^Session %2) %2) sessions))
        (.init impl {})
        (reset! latch (:sids @state))
        (->> (fmtStartBody impl sessions)
             (publicEvent<> Events/START)
             (.broadcast room)))

      (restart [this arg]
        (log/debug "arena#restart() called")
        (->> (fmtStartBody impl (:sessions @state))
             (publicEvent<> Events/RESTART)
             (.broadcast room)))
      (restart [_] (.restart _ nil))

      (start [_ arg]
        (log/info "arena#start called")
        (.start impl arg))
      (start [_] (.start _ nil))

      (stop [this]
        (.broadcast room
                    (publicEvent<> Events/STOP nil)))

      (update [this evt]
        (let [{:keys [context body]} evt
              sid (. ^Session context id)
              snum (. ^Session context number)]
          (cond
            (and (isPrivate? evt)
                 (isCode? Events/REPLAY evt))
            (locking _latch_mutex_
              (when (empty? @latch)
                (reset! latch (:sids @state))
                (.restart this)))

            (and (isPrivate? evt)
                 (isCode? Events/STARTED evt))
            (if (contains? @latch sid)
              (locking _latch_mutex_
                (log/debug "latch: take-off: %d" snum)
                (swap! latch dissoc sid)
                (if (empty? @latch)
                  (.start this (readJsonStrKW body)))))

            (and (some? @latch)
                 (empty? @latch))
            (.onEvent impl evt))))

      (dispose [_])
      (state [_] @state)
      (container [_] (:room @state)))))





;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


