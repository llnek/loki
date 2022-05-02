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

  czlab.loki.game.reqs

  (:require [czlab.loki.xpis :as loki]
            [czlab.basal.core :as c]
            [czlab.basal.util :as s]
            [czlab.basal.io :as i]
            [czlab.basal.util :as u]
            [czlab.loki.player :as p]
            [czlab.loki.net.core :as nc]
            [czlab.loki.game.room :as gr]
            [czlab.loki.game.core :as gc])

  (:import [czlab.jasal I18N]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn do-playreq

  "source json = {:gameid, :principal, :credential}"
  [{:keys [source socket body] :as evt}]

  (let [{:keys [gameid principal credential]}
        body
        rcb (some-> source
                    c/parent c/id?? I18N/bundle)]
    (if (c/hgl? (c/sname gameid))
      (let
        [gameid (keyword gameid)
         g (gc/lookup-game gameid)
         p (if (some? g)
             (p/lookup-player principal
                             (u/x->chars credential)))
         s (if (and (some? g)
                    (some? p)) (gr/open-room g p evt))]
        (c/debug "game[%s] loaded as: %s" gameid g)
        (cond
          (nil? g)
          (c/do->nil
            (nc/reply-error socket
                            loki/game-nok
                            (u/rstr rcb "game.notok")))
          (nil? p)
          (c/do->nil
            (nc/reply-error socket
                            loki/user-nok
                            (u/rstr rcb "login.error")))
          (nil? s)
          (c/do->nil
            (nc/reply-error socket
                            loki/rooms-full
                            (u/rstr rcb "room.none")))
          :t s))
      (c/do->nil
        (nc/reply-error socket
                        loki/playreq-nok
                        (u/rstr rcb "bad.req"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request to join a specific game room.  Not used now.
;; source json = [gameid, roomid, userid, password]
(defn do-joinreq

  "Request to join a specific game room.  Not used now.
  source json = {:gameid, :roomid, :principal, :credential}"
  [{:keys [source socket body] :as evt}]

  (let [{:keys [gameid roomid principal credential]}
        body
        rcb (some-> source
                    c/parent c/id?? I18N/bundle)]
    (if (and (c/hgl? (c/sname gameid))
             (c/hgl? (c/sname roomid)))
      (let
        [p (p/lookup-player principal credential)
         gameid (keyword gameid)
         roomid (keyword roomid)
         pss (some-> p
                     (gr/join-room  gameid roomid evt))]
        (cond
          (nil? p)
          (c/do->nil
            (nc/reply-error socket
                            loki/user-nok
                            (u/rstr rcb "login.error")))
          (nil? pss)
          (c/do->nil
            (nc/reply-error socket
                            loki/room-nok
                            (u/rstr rcb "room.bad")))
          :else pss))
      (c/do->nil
        (nc/reply-error socket
                        loki/joinreq-nok
                        (u/rstr rcb "bad.req"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

