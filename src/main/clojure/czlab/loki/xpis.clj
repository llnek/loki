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
;; Copyright © 2013-2022, Kenneth Leung. All rights reserved.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.xpis

  (:require [czlab.basal.core :as c]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def type-private 3)
(def type-public 2)
(defn msg-types

  ""
  [v]

  (case v
    type-public "public"
    type-private "private" ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def status-error 500)
(def status-ok 200)
(defn status-codes

  ""
  [v]

  (case v
    status-ok "OK"
    status-error "Internal Server Error" ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def playgame-req 600)
(def playreq-ok 601)
(def playreq-nok 602)
(def joingame-req 651)
(def joinreq-ok 652)
(def joinreq-nok 653)

(def user-nok 700)
(def game-nok 701)
(def room-nok 702)
(def room-filled 703)
(def rooms-full 704)

(def player-joined 800)
(def await-start 801)

(def connected 820)
(def started 821)
(def closed 822)
(def tear-down 823)

(def restart 840)
(def start 841)
(def stop 842)
(def start-nxt-round 843)
(def end-cur-round 844)

(def poke-rumble 860)
(def poke-move 861)
(def poke-wait 862)
(def sync-arena 863)

(def replay 880)
(def play-move 881)
(def play-scrubbed 882)

(def game-won 890)
(def game-tie 891)

(def quit 911)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn msg-codes

  ""
  [v]

  (case v
    playgame-req "playgame-req"
    playreq-ok "playreq-ok"
    playreq-nok "playreq-nok"
    joingame-req "joingame-req"
    joinreq-ok "joinreq-ok"
    joinreq-nok "joinreq-nok"
    user-nok "user-nok"
    game-nok "game-nok"
    room-nok "room-nok"
    room-filled "room-filled"
    rooms-full "rooms-full"
    player-joined "player-joined"
    await-start "await-start"
    connected "connected"
    started "started"
    closed "closed"
    tear-down "tear-down"
    restart "restart"
    start "start"
    stop "stop"
    start-round "start-round"
    end-round "end-round"
    poke-rumble "poke-rumble"
    poke-move "poke-move"
    poke-wait "poke-wait"
    sync-arena "sync-arena"
    replay "replay"
    play-move "play-move"
    play-scrubbed "play-scrubbed"
    game-won "game-won"
    game-tie "game-tie"
    quit "quit"
    ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol GameBoard
  ""
  (get-next-moves [_ game] "")
  (game-over? [_ game] "")
  (eval-score [_ game] "")
  (unmake-move [_ game move] "")
  (make-move [_ game move] "")
  (switch-player [_ game] "")
  (take-snap-shot [_] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol GameImpl
  ""
  (get-player-gist [_ id] "")
  (start-round [_ arg] "")
  (end-round [_] "")
  (on-game-event [_ evt] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol PubSub
  ""
  (unsub-if-session [_ session] "")
  (unsub [_ handler] "")
  (sub [_ handler] "")
  (pub-event [_ event] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defprotocol GameRoom
  ""
  (broad-cast [_ evt] "")
  (count-players [_] "")
  (can-open-room? [_] "")
  (on-room-event [_ evt] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

