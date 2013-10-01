(ns pump.template
  (:require [clojure.string :as string]))

(declare elem-factory)

(let [cache (js/Object.)]
  (defn dash-to-camel-name
    [k]
    (or (aget cache k)
        (let [words (string/split (name k) #"-")
              camels (map string/capitalize (rest words))
              complete (keyword (apply str (first words) camels))]
          (aset cache k complete)
          complete))))

(defn as-content
  [content]
  (for [c content]
    (cond (nil? c) nil
          (map? c) (throw "Maps cannot be used as content")
          (string? c) c
          (vector? c) (elem-factory c)
          (seq? c) (as-content c)
          :else (str c))))

;; From Weavejester's Hiccup: https://github.com/weavejester/hiccup/blob/master/src/hiccup/compiler.clj#L32
(def ^{:doc "Regular expression that parses a CSS-style id and class from a tag name."
       :private true}
  re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(defn parse-tag
  [tag]
  (if (fn? tag)
    [:custom tag nil]
    (let [[tag id class] (next (re-matches re-tag (name tag)))]
      [:vanilla
       (aget (.-DOM js/React) tag)
       {:id (or id nil)
        :className (if class (string/replace class #"\." " "))}])))

(def attr-mapping
  {:class :className
   :for :htmlFor})

(defn normalize-into
  [tag-attrs attrs]
  (into tag-attrs (map
                   (fn [[k v]] [(dash-to-camel-name (attr-mapping k k)) v])
                   attrs)))

(defn exclude-empty
  [attrs]
  (into {} (filter (fn [[k v]] v) attrs)))

(defn normalize-element
  "Ensure an element vector is of the form [tag-name attrs content]."
  [[tag & content]]
  (when (not (or (keyword? tag) (symbol? tag) (string? tag) (fn? tag)))
    (throw (str tag " is not a valid element name.")))
  (let [[tag-type tag tag-attrs] (parse-tag tag)
        map-attrs      (first content)]
    (if (map? map-attrs)
      [tag-type tag (if (= tag-type :vanilla)
                      (normalize-into tag-attrs map-attrs)
                      map-attrs)
       (next content)]
      [tag-type tag tag-attrs content])))

(defn elem-factory
  [elem-def]
  (let [[tag-type tag-fn attrs content] (normalize-element elem-def)
        attrs (exclude-empty attrs)
        attrs (if (= tag-type :vanilla) (clj->js attrs) attrs)]
    (if (nil? tag-fn)
      (throw (str "Element definition '" (pr-str elem-def) "' could not be parsed")))
    (tag-fn attrs (clj->js (as-content content)))))

(defn html [& tags]
  (let [res (map elem-factory tags)]
    (if (second res)
      res
      (first res))))
