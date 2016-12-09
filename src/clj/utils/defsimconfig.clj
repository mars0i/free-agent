;; Define a defsymstate macro that will define a subclass of MASON's
;; SimState with associated instance state variable, accessors, etc.

(ns utils.defsimconfig
  (:require [clojure.string :as s]))

(def class-sym 'SimConfig)
(def class-constructor 'SimConfig.)
(def data-class-sym 'SimConfigData)
(def data-sym 'simConfigData)
(def data-accessor '.simConfigData)
(def init-sym 'init-sim-config-data)

(defn hyphed-to-studly-str
  "Converts a hyphenated string into the corresponding studly caps string."
  [string]
  (let [parts (s/split string #"-")]
    (reduce str (map s/capitalize parts))))

(defn hyphed-sym-to-studly-str
  "Convience wrapper for hyphed-to-studly-str that converts symbol to
  string before calling it."
  [sym]
  (hyphed-to-studly-str (name sym)))


(defn make-accessor-sym
  "Given a prefix string and a Clojure symbol, returns a Java 
  Bean-style accessor symbol using the prefix.  e.g.:
  (make-accessor-sym \"get\" this-and-that) ;=> getThisAndThat"
  [prefix stub-str]
  (symbol (str prefix stub-str)))

(defn make-accessor-sigs
  [get-syms set-syms classes]
  (mapcat (fn [get-sym set-sym cls] [[get-sym [] cls] [set-sym [cls] 'void]])
               get-syms set-syms classes))

(defn get-range-fields
  "Given a fields argument to defsimconfig, return a sequence containing 
  only those field specifications that include specification of the default
  range of values for the field."
  [fields]
  (filter #(= 4 (count %)) 
          fields))

(defmacro defsimconfig
  "sim-state-class is a fully-qualified name for the new class. fields is a
  sequence of 2- or 4-element sequences starting with names of fields in which 
  configuration data will be stored and accessed.  Second elements are Java type 
  identifiers for these fields.  If there is a third and fourth element, they
  are the min and max values for the field.  The following gen-class options will be 
  automatically provided: :state, :exposes-methods, :init, :main, :methods.  
  Additional options can be provided in addl-gen-class-opts."
  [class-prefix fields & addl-gen-class-opts]
   (let [qualified-class (symbol (str class-prefix "." class-sym))
         field-syms (map first fields)
         field-keywords (map keyword field-syms)
         default-syms (map #(symbol (str "default-" %)) field-syms)
         accessor-stubs (map hyphed-sym-to-studly-str field-syms)
         get-syms  (map (partial make-accessor-sym "get") accessor-stubs)
         set-syms  (map (partial make-accessor-sym "set") accessor-stubs)
         -get-syms (map (partial make-accessor-sym "-") get-syms)
         -set-syms (map (partial make-accessor-sym "-") set-syms)
         range-fields (get-range-fields fields)
         dom-syms  (map (comp (partial make-accessor-sym "dom") hyphed-sym-to-studly-str first)
                        range-fields)
         -dom-syms (map (partial make-accessor-sym "-") dom-syms)
         dom-keywords (map keyword dom-syms)
         ranges (map nnext range-fields)
         gen-class-opts {:name qualified-class
                         :state data-sym
                         :exposes-methods '{start superStart}
                         :init init-sym
                         :main true
                         :methods (vec (concat (make-accessor-sigs get-syms set-syms (map second fields))
                                               (map #(vector % [] java.lang.Object) dom-syms)))} 
         gen-class-opts (into gen-class-opts 
                              (map vec (partition 2 addl-gen-class-opts)))]
     `(do
        ;;;; should be in a different namespace (so simulation code can access it without cyclicly referencing SimConfig):
        (defrecord ~data-class-sym ~(vec field-syms)) ; TODO make sure SimConfigData comes out in the right namespace
        ;;;; should be in SimConfig namespace:
        (gen-class ~@(apply concat gen-class-opts))
        ;;;; Should be in same namespace as gen-class:
        (defn ~init-sym [~'seed] [[~'seed] (atom (~class-constructor ~@default-syms))]) ; NOTE will fail if default-syms are not yet defined.
        ;; need to add type annotations:
        (import ~qualified-class) ; must go after gen-class but before any type annotations using the class
        ~@(map (fn [sym keyw] (list 'defn sym '[this] `(~keyw @(.simConfigData ~'this))))
               -get-syms field-keywords)
        ~@(map (fn [sym keyw] (list 'defn sym '[this newval] `(swap! (~data-accessor ~'this) assoc ~keyw  ~'newval)))
               -set-syms field-keywords)
        ~@(map (fn [sym keyw range-pair] (list 'defn sym '[this] `(Interval. ~@range-pair)))
               -dom-syms dom-keywords ranges)
        )))


