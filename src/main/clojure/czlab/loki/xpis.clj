;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.xpis

  (:require [czlab.basal.logging :as log])

  (:use [czlab.basal.core]
        [czlab.basal.str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(decl-special-enum loki-msg-types

                   protected 1
                   public 2
                   private 3)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(decl-special-enum loki-status-codes

                   ok 200
                   error 500)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(decl-special-enum loki-msg-codes

                   playgame-req 600
                   playreq-ok 601
                   playreq-nok 602
                   joingame-req 651
                   joinreq-ok 652
                   joinreq-nok 653

                   user-nok 700
                   game-nok 701
                   room-nok 702
                   room-filled 703
                   rooms-full 704

                   player-joined 800
                   await-start 801

                   connected 820
                   started 821
                   closed 822
                   tear-down 823

                   restart 840
                   start 841
                   stop 842
                   start-round 843
                   end-round 844

                   poke-rumble 860
                   poke-move 861
                   poke-wait 862
                   sync-arena 863

                   replay 880
                   play-move 881
                   play-scrubbed 882

                   game-won 890
                   game-tie 891

                   quit 911)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
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
;;
(defprotocol GameLogic ;;extends Initable, Startable, Restartable {
  ""
  (get-player-gist [_ id] "")
  (start-round [_ arg] "")
  (end-round [_] "")
  (on-game-event [_ evt] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol PubSub
  ""
  (unsubsc-if-session [_ session] "")
  (unsubsc [_ handler] "")
  (subsc [_ handler] "")
  (pub-event [_ event] ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol GameRoom
  ""
  (broad-cast [_ evt] "")
  (count-players [_] "")
  (can-open-room? [_] "")
  (on-room-event [_ evt] ""))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

