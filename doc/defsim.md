Use of pasta.defsim/defsim in pasta.Sim
====

### Rationale

`defsim/defsim` is a macro with two goals:

    1. Generate a series of coordinated definitions.
    2. Move global configuration data into its own namespace.

The purpose of the second goal is to avoid problems with cyclic
dependencies when adding many type hints to avoid reflection.  I haven't
tested whether this accomplishes its goals, however; I haven't added
enough type hints.  (I didn't need the extra speed for pasta
simulation.)

The reason for the first goal is that making Mason provides a
convenient way to allow users to customize configuration variables in
the GUI.  If you set up certain Java bean-style accessors for your
configuation variables,  the configuration controls magically appear.
However, Mason is designed to be used in a way that's perfectly
reasonable in Java, but that Clojure's designers don't really like.
Clojure will do what's necessary, but it doesn't go out of its way to
make it convenient.  As a result, for each variable that you want to
be configurable via the GUI, you need to provide:

1. Two to three bean-ish accessor functions.
2. Two to three corresponding signatures, in another part of your code.
3. An entry in a defrecord, defined somewhere else.
4. A value for that entry in an intializer function, defined somewhere
else again.
5. Optionally, a commandline option that will allow setting the
variable from the command line.

So when you add, delete, or change the definion of a configuration
varialbe, all the above elements have to be kept coordinated.

`defsim` does that for you: You pass it a single line of configuration
info, and it does the rest.  This means that it does a lot of things in
way that's usually hidden, and you just have to know part of what it's
doing (see below), but the alternative is worse.

### Using `defsim`

#### Things you must provide:

`defsim` requires that the subclass of Mason's `SimState` be
named `Sim`.  (It wouldn't be hard to modify defsim.clj to allow it to
discover the name of the `SimState` subclass, but I don't have a need
for this change.  If you use `defsim`, nd this bothers you let me
know and I'll fix it.)

You also must typically precede the call to `defsim` with this:

```clojure
(def commandline$ (atom nil))
```

I couldn't figure out how to move this into the `defsim` definition.
(I name variables that contain atoms with "$" as a suffix.)

#### The `defsim` call

Example of the use of `defsim` in Sim.clj:

```clojure
;;              field name   initial-value  type   in ui? with range?  info for clojure's cli commandline option function
(defsim/defsim [[num-k-snipes       25      long    [0 500]     ["-K" "Size of k-snipe subpopulation" :parse-fn #(Long. %)]]
                [mush-prob           0.2    double  [0.0 1.0]   ["-M" "Average frequency of mushrooms." :parse-fn #(Double. %)]]
                [mush-low-size       4.0    double  true        ["-s" "Size of small mushrooms (mean of light distribution)" :parse-fn #(Double. %)]]
                [mush-mid-size       0      double  false] ; calculated from the previous values
                [use-gui           false    boolean false       ["-g" "If -g, use GUI; otherwise use GUI if and only if +g or there are no commandline options." :parse-fn #(Boolean. %)]]
                [csv-basename       nil java.lang.String false  ["-f" "Base name of files to append data to.  Otherwise new filenames generated from seed." :parse-fn #(String. %)]]
                [popenv             nil  pasta.popenv.PopEnv false]]
  :methods [[getPopSize [] long] ; additional options here. this one is for def below; it will get merged into the generated :methods component.
            [getKSnipeFreq [] double]])
```

The comments above the call describe the elements of the first argument.
I show below what this expands to in another section.

For each element in that first argument--a vector of vectors--`defsim`
generates code that performs some or all of the five functions listed in
a previous section.  

Along the way, `defsim` defines, in a separate namespace
`<your
prefix>.data`, a defrecord named `SimData`.  An instance of this
defrecord will be put into the "state" variable of your `Sim` class
instance (i.e. the class that inherits from Mason's `sim.engine.SimState`).
This state variable, named `simData`, is the only instance variable that 
Clojure's `gen-class` allows.  We wrap a `SimData` record in an atom, 
and make that atom the value of `Sim`'s instance variable `simData`.

The fields of the `SimData` defrecord are named by the first elements
of the inner vectors in the first argument to `defsim`.

Values of the fields in this `SimData` are initialized when your `Sim`
class is created.  The initial values are the second elements of the inner
vectors in `defsim`'s argument.

The remaining elements in the inner vectors are used to define (a)
Bean-style accessor functions that Mason will use to create GUI elements
which will allow a user to change the values in the `SimData` defrecord
(using `swap!` and `assoc` behind the scenes), and (b) command line options
that allow setting these same values.  The docstring below says a bit more
about this.

Here is `defsim`'s docstring (lightly formatted):

`defsim`  
([fields & addl-gen-class-opts])  
Macro  
`defsim` generates Java-bean style and other MASON-style accessors; a gen-class
expression in which their signatures are defined along with an instance
variable containing a Clojure map for their corresponding values; an
initializer function for the map; and a call to
clojure.tools.cli/parse-opts to define corresponding commandline
options.  `fields` is a sequence of 4- or 5-element sequences starting
with names of fields in which configuration data will be stored and
accessed, followed by initial values and a Java type identifiers for the
field.  The fourth element is either false to indicate that the field
should not be configurable from the UI, or truthy if it is.  In the
latter case, it may be a two-element sequence containing default min and
max values to be used for sliders in the UI.  (This range doesn't
constraint fields' values in any other respect.) The fifth element, if
present, specifies short commandline option lists for use by
`cli-options`, except that the second, long option specifier should be
left out; it will be generated from the parameter name.  The following
`gen-class` options will automatically be provided in the expansion of
`defsim`: `:state`, `:exposes-methods`, `:init`, `:main`, `:methods`.
Additional options can be provided in `addl-gen-class-opts` by
alternating `gen-class` option keywords with their intended values.  If
`addl-gen-class-opts` includes `:exposes-methods` or `:methods`, the
value(s) will be combined with automatically generated values for these
`gen-class` options.  Note: defsim must be used only in a namespace
named &lt;namespace prefix&gt;.Sim, where &lt;namespace prefix&gt; is the path
before the last dot of the current namespace.  Sim must be aot-compiled
in order for gen-class to work.

See the expansion of the above code, below, for details about what
`defsim` does.


#### Accessing configuration data

You can access the global configuration data in the `SimData` defrecord
stored in the `simData` instance variable of your class `Sim` by getting
`simData` from `Sim` and then defref'ing the atom inside `simData`.  For
example, if `sim` contains your `Sim` instance:

```clojure
(let [sim-data$ (.simData sim)
      sim-data @sim-data$
      my-config-param-1 (:my-config-param-1 sim-data)
      my-config-param-2 (:my-config-param-2 sim-data)]
   (do-things-with my-config-param-1 my-config-param-2))
```

Or you can do it in one step:

```clojure
   (do-something-with (:my-config-param-3 @(.simData sim)))
```

For example, I do this in the `-start` function in Sim.clj. 

To use this configuration data, your code obviously has to have had
access to the `Sim` instance at some point.  Some examples:

In the `-start` function in `Sim`, the `Sim` instance is passed as the
sole argument, which would often be called `this`.

If your `-start` function calls or schedules some central routines
that run the simulation, you can pass in your `Sim` instance,
the atom wrapping your `SimData`, or the `SimData` itself, so that
your code can access the configuration data stored in it.

In your GUI class--let's say it's named "UI"--which inherits from
Mason's `sim.display.GUIState`, the `Sim` instance will usually be
accessible using the `getState()` accessor that `UI` inherits from
`GUIState`.  For example, your `setup-portrayals` function might start
like this:

```clojure
(defn setup-portrayals
  [this-ui]
  (let [sim (.getState this-ui)
        sim-data$ (.simData sim)
           ...                  ]
    ...))
```

### What `defsim` expands to

To see what your `defim` call does, you can pass the quoted (i.e. with
`'`) expression containing it to `macroexpand-1`.  You might also want
to pass the output of `macroexand-1` to `pprint`.

For example, the `defsim` example above expands to the following code.
I've added comments that are not generated with the code.

```clojure
(do
  ;; Switch from Sim namespace to data namespace that's created here.
  (clojure.core/ns pasta.data)
  ;; Define a data record structure in the data namespace (cf. defsim goal 2 above).
  (clojure.core/defrecord SimData [num-k-snipes mush-prob mush-low-size mush-mid-size use-gui csv-basename popenv])

  ;; Now switch back to the Sim namespace.  The options specified in the ns
  ;; call from the top of the Sim.clj source file are carried over.
  (clojure.core/ns pasta.Sim
    (:require [pasta.data])
    (:import pasta.Sim) ; odd to do this in Sim namespace; I found it necessary.
    ;; Use gen-class here *instead of* in the ns call at top of Sim.clj.
    ;; This will generate Java class files so that Mason can find the Sim class.
    (:gen-class
      :name pasta.Sim
      :extends sim.engine.SimState ; Subclass Mason's central class SimState
      :state simData ; This will be a map containing instance data.
      :exposes-methods {start superStart} ; Allow calling start in SimState by name superStart.
      :init init-sim-data ; function that will populate simState in class instance 
      :main true ; Yes, we want to expose a main routine
      ;; Instance methods that we want exposed. Automatically generated by 
      ;; defsim for vector elements in the first argument whose fourth element
      is non-falsey.  These will cause Mason to allow data elements in our
      ;; SimData to be editable in the Model tab of the GUI.
      :methods [[getNumKSnipes [] long]
                [setNumKSnipes [long] void]
                [getMushProb [] double]
                [setMushProb [double] void]
                [getMushLowSize [] double]
                [setMushLowSize [double] void]
                [domNumKSnipes [] java.lang.Object] ; Because 4th arg was pair
                [domMushProb [] java.lang.Object]   ; these methods generated.
                [getPopSize [] long]         ; These two not auto-generated;
                [getKSnipeFreq [] double]])) ; they come from :methods arg.
  ;; end of gen-class
  ;; 
  ;; Define initializing function automatically called on instance creation.  This just
  ;; returns a two-element vector in which the first element contains arguments to be
  ;; passed to the superclass constructor, and the second argument contains and atom that
  ;; will contain a defrecord that stores the global config data we want.
  (clojure.core/defn -init-sim-data
                 [seed]
		 [[seed] (clojure.core/atom (pasta.data/->SimData 25 0.2 4.0 0 false nil nil))])
  ;; The :methods value in gen-class exposed methods to Java, but doesn't
  ;; define them.  Here they are defined:
  (defn -getNumKSnipes [this] (:num-k-snipes @(.simData this)))
  (defn -getMushProb [this] (:mush-prob @(.simData this)))
  (defn -getMushLowSize [this] (:mush-low-size @(.simData this)))
  (defn -setNumKSnipes [this newval] (clojure.core/swap! (.simData this) clojure.core/assoc :num-k-snipes newval))
  (defn -setMushProb [this newval] (clojure.core/swap! (.simData this) clojure.core/assoc :mush-prob newval))
  (defn -setMushLowSize [this newval] (clojure.core/swap! (.simData this) clojure.core/assoc :mush-low-size newval))
  (defn -domNumKSnipes [this] (Interval. 0 500))
  (defn -domMushProb [this] (Interval. 0.0 1.0))
  ;; Define method that will gather and store command line options
  ;; (cf. documentation for clojure.tools.cli/parse-opts):
  (clojure.core/defn record-commandline-args!
    "Temporarily store values of parameters passed on the command line."
    [args__1358__auto__]
    (clojure.core/let [cli-options [["-?" "--help" "Print this help message."]
                                    ["-K" "--num-k-snipes <long> (25)" "Size of k-snipe subpopulation" :parse-fn (fn* [p1__1318#] (Long. p1__1318#))]
                                    ["-M" "--mush-prob <double> (0.2)" "Average frequency of mushrooms." :parse-fn (fn* [p1__1319#] (Double. p1__1319#))]
                                    ["-s" "--mush-low-size <double> (4.0)" "Size of small mushrooms (mean of light distribution)" :parse-fn (fn* [p1__1320#] (Double. p1__1320#))]
                                    ["-g" "--use-gui" "If -g, use GUI; otherwise use GUI if and only if +g or there are no commandline options." :parse-fn (fn* [p1__1321#] (Boolean. p1__1321#))]
                                    ["-f" "--csv-basename <java.lang.String> ()" "Base name of files to append data to.  Otherwise new filenames generated from seed." :parse-fn (fn* [p1__1322#] (String. p1__1322#))]]
                       usage-fmt__1359__auto__ (clojure.core/fn [options]
                                                 (clojure.core/let [fmt-line (clojure.core/fn [[short-opt long-opt desc]]
						                                (clojure.core/str short-opt ", " long-opt ": " desc))]
                                                   (clojure.string/join "\n" (clojure.core/concat (clojure.core/map fmt-line options)))))
                       {:as cmdline, :keys [options arguments errors summary]} (clojure.tools.cli/parse-opts args__1358__auto__ cli-options)]
      (clojure.core/reset! commandline$ cmdline) ; store the commandline in an atom for later processing
      (clojure.core/when (:help options)
        (clojure.core/println "Command line options (defaults in parentheses):")
        (clojure.core/println (usage-fmt__1359__auto__ cli-options))
        (clojure.core/println "MASON options can also be used:")
        (clojure.core/println "-help (note single dash): Print help message for MASON.")
        (java.lang.System/exit 0)))))
```

I generated this at the repl by lightly editing the output of:

1. Executing the `ns` expression at the top of `Sim.clj` at the repl
2. Running 

```
(use 'pasta.defsim)
(clojure.pprint (macroexpand-1 '<paste defsim code from above>))
```

```

