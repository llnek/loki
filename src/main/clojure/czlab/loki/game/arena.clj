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

  (:require [czlab.loki.xpis :as loki :refer :all]
            [czlab.basal.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.loki.net.core]
        [czlab.loki.net.disp]
        [czlab.wabbit.xpis]
        [czlab.basal.format]
        [czlab.basal.core]
        [czlab.basal.io]
        [czlab.basal.str]
        [czlab.loki.session])

  (:import [java.util.concurrent.atomic AtomicInteger]
           [czlab.jasal
            Restartable
            Startable
            Initable
            Idable
            Openable
            Receivable
            Sendable
            Dispatchable]
           [java.io Closeable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtStartBody [impl sessions]
  (preduce<map>
    #(let [{:keys [number player]}
           (deref %2)
           yid (id?? player)
           g (get-player-gist impl yid)]
       (assoc! %1
               yid
               (merge {:pnum number} g))) sessions))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(decl-mutable GameArena
  Openable
  (open [me] (.open me nil))
  (open [me _]
    (let [{:keys [disp conns source game]} @me
          sss (sort-by #(:created (deref %)) (vals conns))
          g (@(:implClass game) me sss)]
      (log/debug "activating room [%s]" me)
      (doseq [s sss] (subsc disp (esubr<> s)))
      (copy* me
             {:impl g
              :latch conns
              :starting? false
              :opened? true :active? false})
      (.init ^Initable g nil)
      (bcast! me ::loki/start (fmtStartBody g sss))))
  (close [me]
    (log/debug "closing arena [%s]" me)
    (doseq [[_ s] (:conns @me)]
      (doto s removeSession closeQ))
    (setf! me :conns nil)
    ((:finz @me) (id?? me)))
  Restartable
  (restart [me _]
    (log/debug "arena#restart() called")
    (->> (-> (:impl @me)
             (fmtStartBody (vals (:conns @me))))
         (bcast! me ::loki/restart)))
  (restart [me] (.restart me nil))
  Startable
  (start [me arg]
    (log/debug "arena#start called")
    (setf! me :active? true)
    (.start ^Startable (:impl @me) arg))
  (start [me] (.start me nil))
  (stop [me]
    (setf! me :active? false))
  GameRoom
  (count-players [me] (count (:conns @me)))
  (broad-cast [me evt]
    (pub-event (:disp @me) evt))
  (can-open-room? [me]
    (and (not (:opened? @me))
         (>= (count-players me)
             (:minPlayers (:game @me)))))
  (on-room-event [me evt]
    (let [{:keys [context body]} evt
          {:keys [impl latch conns active?]} @me]
      (assert (some? context))
      (cond
        (and (not active?) (isCode? ::loki/replay evt))
        (locking me
          (when (and (not (:starting? @me))
                     (empty? (:latch @me)))
            (copy* me {:latch conns :starting true})
            (.restart me)))

        (and (not active?) (isCode? ::loki/started evt))
        (if (in? latch (id?? context))
          (locking me
            (log/debug "latch: drop-off: %s" context)
            (setf! me
                   :latch
                   (dissoc (:latch @me)
                           (id?? context)))
            (if (empty? (:latch @me))
              (.start me (readJsonStrKW body)))))

        (and active? (some? latch) (empty? latch))
        (let [rc (on-game-event impl evt)]
          (when (and (isQuit? evt)
                     (= rc ::loki/tear-down))
            (bcast! me
                    ::loki/play-scrubbed
                    {:pnum (:number @context)})
            (pause 1000)
            (.close me))))))
  Sendable
  (send [me msg]
    (cond
      (isPrivate? msg) (some-> ^Sendable
                               (:context msg) (.send msg))
      (isPublic? msg) (broad-cast me msg)))
  Receivable
  (receive [me evt]
    (when (:opened? @me)
      (log/debug "room recv'ed msg %s" evt)
      (cond
        (isPublic? evt) (broad-cast me evt)
        (isPrivate? evt) (on-room-event me evt)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro arena<> "" [game finzer source]
  `(mutable<> GameArena {:numctr (AtomicInteger.)
                         :disp (edispatcher<>)
                         :id (keyword (uid<>))
                         :shutting? false
                         :opened? false
                         :active? false
                         :source ~source
                         :finz ~finzer
                         :game ~game
                         :conns {} }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


