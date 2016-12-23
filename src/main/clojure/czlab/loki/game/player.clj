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

  czlab.loki.game.player

  (:require [czlab.xlib.logging :as log])

  (:use [czlab.xlib.core]
        [czlab.xlib.io]
        [czlab.xlib.str])

  (:import [czlab.loki.core
            Game
            Room
            Player
            Session]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; player-db
;; {player-id -> {:p player :s {id -> session}}}
(def ^:private PLAYER-DB (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn removePlayer
  ""
  ^Player
  [^String user]
  (when-some [m (@PLAYER-DB user)]
    (swap! PLAYER-DB dissoc user)
    (doseq [[_ v] (:s m)] (closeQ v))
    (:p m)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- player<>
  ""
  ^Player
  [^String user ^String pwd]
  {:pre [(hgl? user)]}
  (let [impl (muble<>)]
    (reify

      Player

      (setEmailId [_ email] (.setv impl :email email))
      (emailId [_] (.getv impl :email))

      (setName [_ n] (.setv impl :name n))
      (name [_] (.getv impl :name))
      (id [_] user)

      (removeSession [_ ps]
        (if-some [m (@PLAYER-DB user)]
          (swap! PLAYER-DB
                 assoc
                 user
                 (update-in m
                            [:s]
                            dissoc (.id ps)))))

      (countSessions [_]
        (if-some [m (@PLAYER-DB user)]
          (int (count (:s m)))
          (int 0)))

      (addSession [_ ps]
        (let [m (@PLAYER-DB user)]
          (swap! PLAYER-DB
                 assoc
                 user
                 (update-in m
                            [:s]
                            assoc (.id ps) ps))))

      (logout [_]
        (removePlayer user)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn createPlayer
  ""
  ^Player
  [^String user ^String pwd]
  (if-some [m (@PLAYER-DB user)]
    (:p m)
    (let [p2 (player<> user pwd)]
      (swap! PLAYER-DB
             assoc user {:p p2 :s {}})
      p2)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupPlayer
  ""
  {:tag Player}
  ([user pwd] (createPlayer user pwd))
  ([user] (:p (@PLAYER-DB user))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

