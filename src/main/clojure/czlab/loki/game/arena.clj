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
;; Copyright © 2013-2024, Kenneth Leung. All rights reserved.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.game.arena

  (:require [czlab.loki.net.core :as nc]
            [czlab.loki.net.disp :as dp]
            [czlab.loki.xpis :as loki]
            [clojure.java.io :as io]
            [czlab.basal.core :as c]
            [czlab.basal.io :as i]
            [czlab.basal.util :as u]
            [czlab.loki.session :as ss])

  (:import [java.io Closeable]
           [java.util.concurrent.atomic AtomicInteger]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- fmtStartBody

  "Collect all the player's session infos."
  [impl sessions]

  (c/preduce<map>
    #(let [{:keys [number player]}
           (c/deref-atomic-core?? %2)
           yid (c/id?? player)
           g (loki/get-player-gist impl yid)]
       (assoc! %1
               yid
               (merge {:pnum number} g))) sessions))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(c/decl-object<> Arena
                 c/AtomicGS
                 (getf [me n]
                       (get (c/deref-atomic-core?? me) n))
                 (setf [me n v]
                       (swap! (c/atomic-core?? me) assoc n v))
                 c/Sendable
                 (send [me msg]
                       (cond
                         (nc/is-private? msg)
                         (some-> (:session msg) (c/send msg))
                         (nc/is-public? msg)
                         (loki/broad-cast me msg)))
                 c/Receivable
                 (receive [me evt]
                          (when (.getf me :opened?)
                            (c/debug "room recv'ed msg %s" (nc/pretty-event evt))
                            (cond
                              (nc/is-public? evt)
                              (loki/broad-cast me evt)
                              (nc/is-private? evt)
                              (loki/on-room-event me evt))))
                 c/Idable
                 (id [me] (.getf me :id))
                 c/Openable
                 (open [me] (.open me nil))
                 (open [me _]
                       (let [{:keys [disp conns source game]} (c/deref-atomic-core?? me)
                             sss (sort-by #(c/getf % :created) (vals conns))
                             ;;create the game implementation
                             g (@(:implClass game) me sss)]
                         (c/debug "activating room [%s]" (.id me))
                         (doseq [s sss]
                           (loki/sub disp (dp/esubr<> s)))
                         (swap! (c/atomic-core?? me)
                                merge {:starting? false
                                       :latch conns
                                       :impl g
                                       :opened? true :active? false})
                         (c/init g nil)
                         (nc/bcast! me loki/start (fmtStartBody g sss))))
                 Closeable
                 (close [me]
                        (c/debug "closing arena [%s]" (.id me))
                        (doseq [[_ s] (.getf me :conns)]
                          (doto s ss/remove-session i/klose))
                        (.setf me :conns nil)
                        ((.getf me :finz) (.id me)))
                 c/Restartable
                 (restart [me]
                          (c/debug "arena [%s] restart() called" (.id me))
                          (nc/bcast! me
                                     loki/restart
                                     (fmtStartBody (.getf me :impl)
                                                   (sort-by #(c/getf % :created)
                                                            (vals (.getf me :conns))))))
                 c/Startable
                 (start [me arg]
                        (c/debug "arena [%s] start called" (.id me))
                        (.setf me :active? true)
                        (loki/start-round (.getf me :impl) arg))
                 (start [me] (.start me nil))
                 (stop [me]
                       (.setf me :active? false))
                 loki/GameRoom
                 (broad-cast [me evt]
                             (loki/pub-event (.getf me :disp) evt))
                 (count-players [me]
                                (count (.getf me :conns)))
                 (can-open-room? [me]
                                 (and (not (.getf me :opened?))
                                      (>= (loki/count-players me)
                                          (:minPlayers (.getf me :game)))))
                 (on-room-event [me evt]
                                (let [{:keys [session body]} evt
                                      _ (assert (some? session))
                                      cid (c/id session)
                                      {:keys [impl latch
                                              conns active?]}
                                      (c/deref-atomic-core?? me)]
                                  (cond
                                    (and (not active?)
                                         (nc/is-code? loki/replay evt))
                                    (locking me
                                      (when (and (not (.getf me :starting?))
                                                 (empty? (.getf me :latch)))
                                        (swap! (c/atomic-core?? me)
                                               merge
                                               {:latch conns :starting true})
                                        (c/restart me)))
                                    (and (not active?)
                                         (nc/is-code? loki/started evt))
                                    (if (contains? latch cid)
                                      (locking me
                                        (c/debug "latch: drop-off: %s" cid)
                                        (.setf me
                                               :latch
                                               (dissoc (.getf me :latch) cid))
                                        (if (empty? (.getf me :latch))
                                          (c/start me (i/read-json body)))))
                                    (and active?
                                         (some? latch) (empty? latch))
                                    (let [rc (loki/on-game-event impl evt)]
                                      (when (and (nc/is-quit? evt)
                                                 (= rc loki/tear-down))
                                        (nc/bcast! me
                                                   loki/play-scrubbed
                                                   {:pnum (c/getf session :number)})
                                        (u/pause 1000)
                                        (.close me)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn arena<>

  ""
  [game finzer source]

  (c/atomic<> Arena {:shutting? false
                     :opened? false
                     :active? false
                     :source source
                     :finz finzer
                     :game game
                     :conns {}
                     :id (keyword (czlab.basal.util/uid<>))
                     :disp (czlab.loki.net.disp/edispatcher<>)
                     :numctr (java.util.concurrent.atomic.AtomicInteger.)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


