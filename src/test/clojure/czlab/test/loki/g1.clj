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

  czlab.test.loki.g1

  (:require [czlab.loki.game.core :as gc]
            [czlab.loki.game.room :as gr]
            [czlab.loki.game.reqs :as rs]
            [czlab.bixby.exec :as be]
            [czlab.bixby.core :as bc]
            [clojure.java.io :as io]
            [clojure.string :as cs]
            [clojure.test :as ct]
            [czlab.loki.player :as p]
            [czlab.loki.util :as u]
            [czlab.basal.meta :as m]
            [czlab.basal.util :as bu]
            [czlab.basal.core :as c
             :refer [ensure?? ensure-thrown??]]
            [czlab.basal.io :as i]
            [czlab.loki.xpis :as loki]
            [czlab.loki.session :as ss]
            [czlab.loki.net.core :as nc]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn g1Impl

  ""
  [& args]

  (reify
    c/Initable
    (init [me arg] nil)
    loki/GameImpl
    (get-player-gist [me id] nil)
    (start-round [me arg] nil)
    (end-round [me] nil)
    (on-game-event [me evt] nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

