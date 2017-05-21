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

  (:require [czlab.loki.net.core :as nc]
            [czlab.loki.net.disp :as dp]
            [czlab.loki.xpis :as loki]
            [czlab.basal.log :as log]
            [clojure.java.io :as io]
            [czlab.wabbit.xpis :as xp]
            [czlab.basal.format :as f]
            [czlab.basal.core :as c]
            [czlab.basal.io :as i]
            [czlab.basal.str :as s]
            [czlab.loki.session :as ss])

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
  (c/preduce<map>
    #(let [{:keys [number player]}
           (deref %2)
           yid (c/id?? player)
           g (loki/get-player-gist impl yid)]
       (assoc! %1
               yid
               (merge {:pnum number} g))) sessions))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(c/decl-mutable GameArena
  Idable
  (id [me] (:id @me))
  Openable
  (open [me] (.open me nil))
  (open [me _]
    (let [{:keys [disp conns source game]} @me
          sss (sort-by #(:created (deref %)) (vals conns))
          g (@(:implClass game) me sss)]
      (log/debug "activating room [%s]" (c/id?? me))
      (doseq [s sss] (loki/subsc disp (dp/esubr<> s)))
      (c/copy* me
               {:impl g
                :latch conns
                :starting? false
                :opened? true :active? false})
      (.init ^Initable g nil)
      (nc/bcast! me ::loki/start (fmtStartBody g sss))))
  (close [me]
    (log/debug "closing arena [%s]" (c/id?? me))
    (doseq [[_ s] (:conns @me)]
      (doto s ss/removeSession i/closeQ))
    (c/setf! me :conns nil)
    ((:finz @me) (c/id?? me)))
  Restartable
  (restart [me _]
    (log/debug "arena [%s] restart() called" (c/id?? me))
    (->> (-> (:impl @me)
             (fmtStartBody (vals (:conns @me))))
         (nc/bcast! me ::loki/restart)))
  (restart [me] (.restart me nil))
  Startable
  (start [me arg]
    (log/debug "arena [%s] start called" (c/id?? me))
    (c/setf! me :active? true)
    (.start ^Startable (:impl @me) arg))
  (start [me] (.start me nil))
  (stop [me]
    (c/setf! me :active? false))
  loki/GameRoom
  (count-players [me] (count (:conns @me)))
  (broad-cast [me evt]
    (loki/pub-event (:disp @me) evt))
  (can-open-room? [me]
    (and (not (:opened? @me))
         (>= (loki/count-players me)
             (:minPlayers (:game @me)))))
  (on-room-event [me evt]
    (let [{:keys [context body]} evt
          {:keys [impl latch conns active?]} @me]
      (assert (some? context))
      (cond
        (and (not active?) (nc/isCode? ::loki/replay evt))
        (locking me
          (when (and (not (:starting? @me))
                     (empty? (:latch @me)))
            (c/copy* me {:latch conns :starting true})
            (.restart me)))

        (and (not active?) (nc/isCode? ::loki/started evt))
        (if (c/in? latch (c/id?? context))
          (locking me
            (log/debug "latch: drop-off: %s" (c/id?? context))
            (c/setf! me
                     :latch
                     (dissoc (:latch @me)
                             (c/id?? context)))
            (if (empty? (:latch @me))
              (.start me (f/readJsonStrKW body)))))

        (and active? (some? latch) (empty? latch))
        (let [rc (loki/on-game-event impl evt)]
          (when (and (nc/isQuit? evt)
                     (= rc ::loki/tear-down))
            (nc/bcast! me
                      ::loki/play-scrubbed
                      {:pnum (:number @context)})
            (c/pause 1000)
            (.close me))))))
  Sendable
  (send [me msg]
    (cond
      (nc/isPrivate? msg) (some-> ^Sendable
                                  (:context msg) (.send msg))
      (nc/isPublic? msg) (loki/broad-cast me msg)))
  Receivable
  (receive [me evt]
    (when (:opened? @me)
      (log/debug "room recv'ed msg %s" (nc/prettyEvent evt))
      (cond
        (nc/isPublic? evt) (loki/broad-cast me evt)
        (nc/isPrivate? evt) (loki/on-room-event me evt)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro arena<> "" [game finzer source]
  `(czlab.basal.core/mutable<>
     czlab.loki.game.arena.GameArena
     {:numctr (java.util.concurrent.atomic.AtomicInteger.)
      :disp (czlab.loki.net.disp/edispatcher<>)
      :id (keyword (czlab.basal.core/uid<>))
      :shutting? false
      :opened? false
      :active? false
      :source ~source
      :finz ~finzer
      :game ~game
      :conns {} }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


