;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.loki.net.disp

  (:require [czlab.basal.logging :as log]
            [czlab.loki.xpis :as loki]
            [clojure.core.async
             :as cas
             :refer
             [close!
              go
              chan
              >!
              <!
              go-loop]])

  (:use [czlab.loki.net.core]
        [czlab.basal.core]
        [czlab.basal.io]
        [czlab.basal.str])

  (:import [czlab.jasal Sendable Receivable]
           [java.io Closeable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(decl-object EventSubr
  Receivable
  (receive [me evt]
    (when (= (:type me)
             (:type evt))
      (log/debug "[%s]: recv'ed msg: %s" me evt)
      (.send ^Sendable
             (:session me) evt))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro esubr<> "" [session]
  `(object<> EventSubr {:id (toKW "subr#" (seqint2))
                        :session ~session
                        :type ::loki/public }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(decl-atomic EventDispatcher
  PubSub
  (unsubsc-if-session [me s]
    (doseq [[su _] (:handlers @me)
            :let [s (:session su)]
            :when (objEQ? su s)]
      (unsubsc me su)))
  (publish-event [_ msg]
    (log/debug "pub msg = %s" (:code msg))
    (doseq [[_ c] (:handlers @me)]
      (cas/go (cas/>! c msg))))
  (unsubsc [me su]
    (when-some [c (get (:handlers @me) su)]
      (cas/close! c)
      (alter-atomic me
                    update-in
                    [:handlers] dissoc su)))
  (subsc [_ su]
    (let [c (cas/chan 4)]
      (alter-atomic me
                    update-in [:handlers] assoc su c)
      (cas/go-loop []
        (when-some [msg (cas/<! c)]
          (if (= (:type su)
                 (:type msg))
            (.receive ^Receivable su msg))
          (recur)))))
  Closeable
  (close [_]
    (doseq [[_ c] (:handlers @me)]
      (cas/close! c))
    (alter-atomic me
                  assoc :handlers {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro edispatcher<> "" []
  `(atomic<> EventDispatcher
             {:id (toKW "disp#" (seqint2)) :handlers {}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

