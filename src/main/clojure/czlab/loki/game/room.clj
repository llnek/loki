;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.game.room

  (:require [czlab.basal.logging :as log])

  (:use [czlab.convoy.nettio.core]
        [czlab.basal.format]
        [flatland.ordered.map]
        [czlab.basal.core]
        [czlab.basal.str]
        [czlab.basal.io]
        [czlab.loki.system.util]
        [czlab.loki.net.core]
        [czlab.loki.net.disp]
        [czlab.loki.game.session])

  (:import [java.util.concurrent.atomic AtomicInteger]
           [czlab.jasal Sendable Dispatchable]
           [czlab.wabbit.ctl Pluglet]
           [io.netty.channel Channel]
           [clojure.lang Keyword]
           [czlab.loki.game
            Engine
            Game
            GameRoom]
           [czlab.loki.core
            Room
            Player
            Session]
           [czlab.loki.net Events Subr PubSub]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; {game-id -> map
;;             { room-id -> room }}
(def ^:private free-rooms (atom (ordered-map)))
(def ^:private game-rooms (atom (ordered-map)))
(def ^:private vacancy 1000)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn countFreeRooms
  "" [gameid] (count (get @free-rooms gameid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn countGameRooms
  "" [gameid] (count (get @game-rooms gameid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn clearFreeRooms
  "Internal only" {:no-doc true}
  [gameid] (swap! free-rooms assoc gameid (ordered-map)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn clearGameRooms
  "Internal only" {:no-doc true}
  [gameid] (swap! game-rooms assoc gameid (ordered-map)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeGameRoom
  "Remove an active room"
  ^GameRoom [gameid roomid]

  (let [gm (@game-rooms gameid)
        r (get gm roomid)]
    (when (some? r)
      (log/debug "remove room(A): %s, game: %s" roomid gameid)
      (swap! game-rooms update-in [gameid] dissoc roomid)
      r)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeFreeRoom
  "Remove a waiting room"
  ^GameRoom [gameid roomid]

  (let [gm (@free-rooms gameid)
        r (get gm roomid)]
    (when (some? r)
      (log/debug "remove room(F): %s, game: %s" roomid gameid)
      (swap! free-rooms update-in [gameid] dissoc roomid)
      r)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn addFreeRoom
  "Add a new partially filled room
   into the pending set"
  ^GameRoom [^GameRoom room] {:pre [(some? room)]}

  (let [rid (.id room)
        gid (.. room game id)
        m (@free-rooms gid)]
    (log/debug "adding a room(F): %s, game: %s" rid gid)
    (if (some? m)
      (swap! free-rooms update-in [gid] assoc rid room)
      (swap! free-rooms assoc gid (ordered-map rid room)))
    room))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn addGameRoom
  "Move room into the active set"
  ^GameRoom [^GameRoom room] {:pre [(some? room)]}

  (let [rid (.id room)
        gid (.. room game id)
        m (@game-rooms gid)]
    (log/debug "add a room(A): %s, game: %s" rid gid)
    (if (some? m)
      (swap! game-rooms update-in [gid] assoc rid room)
      (swap! game-rooms assoc gid (ordered-map rid room)))
    room))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- detachFreeRoom
  "" [gameid roomid]

  (log/debug "found a room(F): %s, game: %s" roomid gameid)
  (swap! free-rooms
         update-in [gameid] dissoc roomid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupFreeRoom
  "Returns a free room which is detached from the pending set"
  {:tag GameRoom}

  ([gameid roomid]
   (let [gm (@free-rooms gameid)
         ^GameRoom r (get gm roomid)]
     (when (some? r)
       (detachFreeRoom gameid (.id r))
       r)))

  ([game]
   (let [gid (.id ^Game game)
         gm (@free-rooms gid)]
    (when-some
      [^GameRoom r (first (vals gm))]
      (detachFreeRoom gid (.id r))
      r))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupGameRoom
  "" ^GameRoom [gameid roomid]
  (log/debug "looking for room: %s, game: %s" roomid gameid)
  (get (@game-rooms gameid) roomid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- localSubr<>
  "" ^Subr [^Session ps]

  (reify Subr
    (eventType [_] Events/PUBLIC)
    (session [_] ps)
    (receive [me evt]
      (if (== (.eventType me)
              (:type evt))
        (.send ps evt)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onSessionMsg
  "" [^GameRoom room evt]
  (if-some [s (:context evt)] (.send ^Sendable s evt)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- gameRoom<>
  "" ^GameRoom [^Game gameObj {:keys [source]}]

  (let [ctr (.server ^Pluglet source)
        crt (.cljrt ctr)
        engObj (.callEx crt (strKW (.engineClass gameObj))
                            (object-array [(atom {}) (ref {})]))
        impl (muble<> {:shutting? false})
        pcount (AtomicInteger.)
        sessions (atom {})
        disp (dispatcher<>)
        rid (uid<>)
        created (now<>)]
    (reify GameRoom

      (countPlayers [_] (count @sessions))

      (disconnect [_ ps]
        (let [py (.player ps)]
          (swap! sessions dissoc (.id ps))
          (.removeSession py ps)
          (.unsubscribeIfSession disp ps)))

      (connect [this py]
        (let [ps (session<> this
                            py
                            (.incrementAndGet pcount))
              _ {:puid (.id py)
                 :pnum (.number ps)}]
          (swap! sessions assoc (.id ps) ps)
          (.addSession py ps)
          ps))

      (isShuttingDown [_] (boolean (.getv impl :shutting?)))
      (isActive [_] (boolean (.getv impl :active?)))

      (canActivate [this]
        (and (not (.isActive this))
             (>= (.countPlayers this)
                 (.minPlayers gameObj))))

      (broadcast [_ evt] (.publish disp evt))

      (engine [_] engObj)

      (game [_] gameObj)

      (id [_] rid)

      (close [_]
        (doseq [^Session v (vals @sessions)]
          (-> (.player v)
              (.removeSession v))
          (closeQ v))
        (reset! sessions {}))

      (activate [this]
        (let [eng (.engine this)
              sss (sort-by #(.number ^Session %)
                           (vals @sessions))]
          (log/debug "activating room %s" rid)
          (.setv impl :active? true)
          (doseq [s sss]
            (.addHandler this (localSubr<> s)))
          (doto eng
            (.init  sss)
            (.ready  this))))

      (removeHandler [_ h] (.unsubscribe disp h))
      (addHandler [_ h] (.subscribe disp h))

      (send [this msg]
        (condp = (:type msg)
          Events/PRIVATE (if-some [^Sendable
                                s (:context msg)]
                        (.send s msg))
          Events/PUBLIC (.broadcast this msg)
          (log/warn "room.sendmsg: unhandled event %s" msg)))

      (receive [this evt]
        (let [eng (.engine this)]
          (log/debug "room got an event %s" evt)
          (condp = (:type evt)
            Events/PUBLIC (.broadcast this evt)
            Events/PRIVATE (.update eng evt)
            (log/warn "room.onmsg: unhandled event %s" evt))))

      Object

      (hashCode [this] (.hashCode rid))

      (equals [this obj]
        (if (nil? obj)
          false
          (or (identical? this obj)
              (and (= (.getClass this)
                      (.getClass obj))
                   (= (.id ^Room obj)
                      (.id this)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn newFreeRoom
  "" ^Session [^Game game ^Player py options]

  (let [room (gameRoom<> game options)]
    (log/debug "created a new play room: %s" (.id room))
    (.connect room py)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn openRoom
  "" ^Session [^Game game ^Player plyr arg]

  (let [pss (some-> (lookupFreeRoom game)
                    (.connect plyr))
        _ (if (nil? pss)
            (log/debug "failed to find a free room for game: %s" (.id game)))
        ^Session
        pss (or pss
                (newFreeRoom game plyr arg))
        ^GameRoom
        room (some-> pss .room)]
    (when (some? room)
      (let
        [^Channel ch (:socket arg)
         src {:room (.id room)
              :game (.id game)
              :pnum (.number pss)}
         evt (eventObj<> Events/PRIVATE Events/PLAYREQ_OK src)]
        (.bind pss arg)
        (setAKey ch PSSN pss)
        (log/debug "replying back to user: %s" evt)
        (replyEvent ch evt)
        (->> (eventObj<> Events/PUBLIC
                         Events/PLAYER_JOINED
                         {:pnum (.number pss)
                          :puid (.id plyr)})
             (.broadcast room))
        (if (.canActivate room)
          (do
            (log/debug "room has enough players, can activate")
            (addGameRoom room)
            (log/debug "room.canActivate = true")
            (.activate room))
          (addFreeRoom room))))
    pss))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn joinRoom
  "" ^Session
  [^GameRoom room ^Player plyr arg]
  {:pre [(some? room)(some? plyr)]}

  (let [^Channel ch (:socket arg)
        game (.game room)]
    (when (< (.countPlayers room)
             (.maxPlayers game))
      (let [pss (.connect room plyr)
            src {:room (.id room)
                 :game (.id game)
                 :pnum (.number pss)}
            evt (eventObj<> Events/PRIVATE
                            Events/JOINREQ_OK src)]
        (.bind pss arg)
        (setAKey ch PSSN pss)
        (replyEvent ch evt)
        (log/debug "replying back to user: %s" evt)
        (when-not (.isActive room)
          (if (.canActivate room)
            (do
              (log/debug "room has enough players, can activate")
              (addGameRoom room)
              (log/debug "room.canActivate = true")
              (.activate room))
            (addFreeRoom room)))
        pss))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

