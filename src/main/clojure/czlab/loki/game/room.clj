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

  czlab.loki.game.room

  (:require [czlab.xlib.logging :as log])

  (:use [czlab.convoy.netty.core]
        [czlab.xlib.format]
        [flatland.ordered.map]
        [czlab.xlib.core]
        [czlab.xlib.guids]
        [czlab.xlib.str]
        [czlab.xlib.io]
        [czlab.loki.system.util]
        [czlab.loki.event.core]
        [czlab.loki.event.disp]
        [czlab.loki.game.session])

  (:import [java.util.concurrent.atomic AtomicInteger]
           [czlab.wabbit.server Cljshim Container]
           [czlab.xlib Sendable Dispatchable]
           [czlab.wabbit.io IoService]
           [io.netty.channel Channel]
           [clojure.lang Keyword]
           [czlab.loki.core
            Game
            Room
            Player
            Session
            Engine]
           [czlab.loki.event Events Subr PubSub]))

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
  ""
  [gameid]
  (count (get @free-rooms gameid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn countGameRooms
  ""
  [gameid]
  (count (get @game-rooms gameid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn clearFreeRooms
  "Internal only"
  {:no-doc true}
  [gameid]
  (swap! free-rooms assoc gameid (ordered-map)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn clearGameRooms
  "Internal only"
  {:no-doc true}
  [gameid]
  (swap! game-rooms assoc gameid (ordered-map)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeGameRoom
  "Remove an active room"
  ^Room
  [gameid roomid]
  (let [gm (@game-rooms gameid)
        r (get gm roomid)]
    (when (some? r)
      (log/debug "remove room(A): %s, game: %s" roomid gameid)
      (swap! game-rooms
             assoc
             gameid (dissoc gm roomid))
      r)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removeFreeRoom
  "Remove a waiting room"
  ^Room
  [gameid roomid]
  (let [gm (@free-rooms gameid)
        r (get gm roomid)]
    (log/debug "remove room(F): %s, game: %s" roomid gameid)
    (swap! free-rooms
           assoc
           gameid (dissoc gm roomid))
    r))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn addFreeRoom
  "Add a new partially filled room
   into the pending set"
  ^Room
  [^Room room]
  {:pre [(some? room)]}
  (let [rid (.id room)
        g (.game room)
        gid (.id g)
        m (@free-rooms gid)]
    (log/debug "adding a room(F): %s, game: %s" rid gid)
    (swap! free-rooms
           assoc
           gid
           (assoc (or m (ordered-map)) rid room))
    room))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn addGameRoom
  "Move room into the active set"
  ^Room
  [^Room room]
  {:pre [(some? room)]}
  (let [rid (.id room)
        g (.game room)
        gid (.id g)
        m (@game-rooms gid)]
    (log/debug "add a room(A): %s, game: %s" rid gid)
    (swap! game-rooms
           assoc
           gid
           (assoc (or m (ordered-map)) rid room))
    room))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- detachFreeRoom
  ""
  [gMap gameid roomid]
  (log/debug "found a room(F): %s, game: %s" roomid gameid)
  (swap! free-rooms
         assoc
         gameid
         (dissoc gMap roomid)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupFreeRoom
  "Returns a free room which is detached from the pending set"
  {:tag Room}
  ([gameid roomid]
   (let [gm (@free-rooms gameid)
         ^Room r (get gm roomid)]
     (when (some? r)
       (detachFreeRoom gm gameid (.id r))
       r)))
  ([game]
   (let [gid (.id ^Game game)
         gm (@free-rooms gid)]
    (when-some
      [^Room r (first (vals gm))]
      (detachFreeRoom gm gid (.id r))
      r))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupGameRoom
  ""
  ^Room
  [gameid roomid]
  (log/debug "looking for room: %s, game: %s" roomid gameid)
  (get (@game-rooms gameid) roomid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- localSubr<>
  ""
  ^Subr
  [^Session ps]
  (reify Subr
    (eventType [_] Events/LOCAL)
    (session [_] ps)
    (receive [me evt]
      (if (== (.eventType me)
              (:type evt))
        (.send ps evt)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- onSessionMsg
  ""
  [^Room room evt]
  (if-some [s (:context evt)]
    (.send ^Sendable s evt)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- room<>
  ""
  ^Room
  [^Game gameObj {:keys [source]}]

  (let [ctr (.server ^IoService source)
        crt (.cljrt ctr)
        engObj (.callEx crt (.engineClass gameObj)
                            (object-array [(atom {}) (ref {})]))
        impl (muble<> {:shutting? false})
        pcount (AtomicInteger.)
        sessions (atom {})
        disp (dispatcher<>)
        rid (uuid<>)
        created (now<>)]
    (reify Room

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

      (isActive [_] (boolean (.getv impl :active?)))
      (isShuttingDown [_] (.getv impl :shutting?))

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
          Events/UNIT (if-some [^Sendable
                                s (:context msg)]
                        (.send s msg))
          Events/LOCAL (.broadcast this msg)
          (log/warn "room.sendmsg: unhandled event %s" msg)))

      (receive [this evt]
        (let [eng (.engine this)]
          (log/debug "room got an event %s" evt)
          (condp = (:type evt)
            Events/LOCAL (.broadcast this evt)
            Events/UNIT (.update eng evt)
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
  ""
  ^Session
  [^Game game ^Player py options]
  (let [room (room<> game options)]
    (log/debug "created a new play room: %s" (.id room))
    (.connect room py)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn openRoom
  ""
  ^Session
  [^Game game ^Player plyr arg]

  (let [pss (some-> (lookupFreeRoom game)
                    (.connect plyr))
        _ (if (nil? pss)
            (log/debug "failed to find a free room for game: %s" (.id game)))
        ^Session
        pss (or pss
                (newFreeRoom game plyr arg))
        ^Room
        room (some-> pss (.room))]
    (when (some? room)
      (let
        [^Channel ch (:socket arg)
         src {:room (.id room)
              :game (.id game)
              :pnum (.number pss)}
         evt (eventObj<> Events/UNIT Events/PLAYREQ_OK src)]
        (.bind pss arg)
        (setAKey ch PSSN pss)
        (log/debug "replying back to user: %s" evt)
        (replyEvent ch evt)
        (->> (eventObj<> Events/LOCAL
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
  ""
  ^Session
  [^Room room ^Player plyr arg]
  {:pre [(some? room)(some? plyr)]}
  (let [^Channel ch (:socket arg)
        game (.game room)]
    (when (< (.countPlayers room)
             (.maxPlayers game))
      (let [pss (.connect room plyr)
            src {:room (.id room)
                 :game (.id game)
                 :pnum (.number pss)}
            evt (eventObj<> Events/UNIT
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

