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

  czlab.loki.game.core

  (:require [czlab.xlib.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.xlib.core]
        [czlab.xlib.str])

  (:import [czlab.loki.core Game]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:dynamic *game-rego* {})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn initGameRegistry!
  "Initialize the game registry"
  [games]
  {:pre [(map? games)]}
  (alter-var-root #'czlab.loki.game.core/*game-rego*
                  (constantly games)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn lookupGame
  "Find a game from the registry"
  ^Game
  [gameid]
  (when-some [g (*game-rego* (keyword gameid))]
    (let [{:keys [enabled? minp maxp engine]
           :or {enabled? false
                minp 1
                maxp 1
                engine ""}}
          (:network g)]
      (log/debug "found game with uuid = %s" gameid)
      (reify Game
        (supportMultiPlayers [_] (boolean enabled?))
        (maxPlayers [_] (if (spos? maxp)
                          (int maxp)
                          (int 9)))
        (minPlayers [_] (if (spos? minp)
                          (int minp)
                          (int 1)))
        (name [_] (:name g))
        (engineClass [_] engine)
        (gist [_] g)
        (id [_] gameid)
        (unload [_] )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


