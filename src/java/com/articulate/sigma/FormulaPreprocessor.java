/** This code is copyright Articulate Software (c) 2017.  Some portions
 copyright Teknowledge (c) 2003 and reused under the terms of the GNU license.
 This software is released under the GNU Public License <http://www.gnu.org/copyleft/gpl.html>.
 Users of this code also consent, by use of this code, to credit Articulate Software
 and Teknowledge in any writings, briefings, publications, presentations, or
 other representations of any software which incorporates, builds on, or uses this
 code.  Please cite the following article in any publication with references:

 Pease, A., (2003). The Sigma Ontology Development Environment,
 in Working Notes of the IJCAI-2003 Workshop on Ontology and Distributed Systems,
 August 9, Acapulco, Mexico. See also https://github.com/ontologyportal/sigmakee

 This class expects the following to be in the ontology.
 Their absence won't cause an exception, but will prevent correct behavior.
 VariableArityRelation
 subclass
 instance
 SetOrClass

 */
package com.articulate.sigma;

import com.google.common.collect.Sets;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormulaPreprocessor {

    /** ***************************************************************
     * For any given formula, stop generating new pred var instantiations
     * and row var expansions if this threshold value has been exceeded.
     * The default value is 2000.
     */
    private static final int AXIOM_EXPANSION_LIMIT = 2000;

    public static boolean debug = false;

    public static boolean addTypes = true;

    /** ***************************************************************
     * A + is appended to the type if the parameter must be a class
     *
     * @return the type for each argument to the given predicate, where
     * ArrayList element 0 is the result, if a function, 1 is the first
     * argument, 2 is the second etc.
     */
    private ArrayList<String> getTypeList(String pred, KB kb) {

        return kb.kbCache.signatures.get(pred);
    }

    /** ***************************************************************
     */
    private boolean hasFormulaType(Formula form,
                                   HashMap<String,HashSet<String>> varmap) {

        if (debug) System.out.println("hasFormulaType(): form: " + form);
        if (debug) System.out.println("hasFormulaType(): varmap: " + varmap);
        for (HashSet<String> hs : varmap.values()) {
            for (String t : hs)
                if (t.equals("Formula")) {
                    if (debug) System.out.println("hasFormulaType(): has a Formula argument: " + form);
                    return true;
                }
        }
        return false;
    }

    /** ***************************************************************
     * Find the argument type restriction for a given predicate and
     * argument number that is inherited from one of its super-relations.
     * A "+" is appended to the type if the parameter must be a class,
     * meaning that a domainSubclass is defined for this argument in one
     * of the loaded .kif files.  Argument number 0 is used for the return
     * type of a Function.  Asking for a non-existent arg will return null;
     */
    public static String findType(int numarg, String pred, KB kb) {

        ArrayList<String> sig = null;
        if (kb == null || kb.kbCache == null) {
            System.out.println("Error in FormulaPreprocessor.findType(): null cache");
            return null;
        }
        else if (kb.kbCache.signatures == null)
            System.out.println("Error in FormulaPreprocessor.findType(): null cache signatures");
        if (kb.kbCache != null && kb.kbCache.signatures != null)
            sig = kb.kbCache.signatures.get(pred);
        if (sig == null) {
            if (!kb.isInstanceOf(pred, "VariableArityRelation"))
                System.out.println("Error in FormulaPreprocessor.findType(): " +
                        "no type information for predicate " + pred);
            return null;
        }
        if (numarg >= sig.size())
            return null;

        return sig.get(numarg);
    }

    /** ***************************************************************
     * This method tries to remove all but the most specific relevant
     * classes from a List of sortal classes.
     *
     * @param types A List of classes (class name Strings) that
     * constrain the value of a SUO-KIF variable.
     *
     * @param kb The KB used to determine if any of the classes in the
     * List types are redundant.
     *
     * @return void
     */
    public void winnowTypeList(HashSet<String> types, KB kb) {

        long t1 = 0L;
        if (types.size() > 1) {
            Object[] valArr = types.toArray();
            String clX = null;
            String clY = null;
            for (int i = 0; i < valArr.length; i++) {
                boolean stop = false;
                for (int j = 0; j < valArr.length; j++) {
                    if (i != j) {
                        clX = (String) valArr[i];
                        clY = (String) valArr[j];
                        if (clX.equals(clY) || kb.isSubclass(clX, clY)) {
                            types.remove(clY);
                            if (types.size() < 2) {
                                stop = true;
                                break;
                            }
                        }
                    }
                }
                if (stop) break;
            }
        }
        return;
    }

    /** ***************************************************************
     */
    public HashMap<String,HashSet<String>> findTypeRestrictions(Formula form, KB kb) {

        HashMap<String,HashSet<String>> varDomainTypes = computeVariableTypes(form, kb);
        if (debug) System.out.println("findTypeRestrictions: varDomainTypes " + varDomainTypes);
        // get variable types which are explicitly defined in formula
        HashMap<String,HashSet<String>> varExplicitTypes = findExplicitTypesClassesInAntecedent(kb,form);
        if (debug) System.out.println("findTypeRestrictions: varExplicitTypes " + varExplicitTypes);
        // only keep variables which are not explicitly defined in formula
        HashMap<String,HashSet<String>> varmap = new HashMap<String, HashSet<String>>();
        for (String var : varDomainTypes.keySet()) {
            if (!varExplicitTypes.containsKey(var)) {
                // var is not explicitly defined
                varmap.put(var, varDomainTypes.get(var));
            }
            else {
                // var is explicitly defined
                HashSet<String> domainTypes = varDomainTypes.get(var);
                HashSet<String> explicitTypes = varExplicitTypes.get(var);
                HashSet<String> types = new HashSet();
                for (String dt : domainTypes) {
                    if (dt.endsWith("+")) types.add(dt); // '+' denotes domainSubclass
                }
                for (String et : explicitTypes) {
                    if (et.endsWith("+")) types.add(et);  // '+' denotes domainSubclass
                }
                varmap.put(var, types);
            }
        }
        return varmap;
    }

    /** ***************************************************************
     */
    public HashMap<String,HashSet<String>> findAllTypeRestrictions(Formula form, KB kb) {

        HashMap<String,HashSet<String>> varDomainTypes = computeVariableTypes(form, kb);
        if (debug) System.out.println("findTypeRestrictions: varDomainTypes " + varDomainTypes);
        // get variable types which are explicitly defined in formula
        HashMap<String,HashSet<String>> varExplicitTypes = findExplicitTypesClassesInAntecedent(kb,form);
        if (debug) System.out.println("findTypeRestrictions: varExplicitTypes " + varExplicitTypes);
        // only keep variables which are not explicitly defined in formula
        HashMap<String,HashSet<String>> varmap = new HashMap<String, HashSet<String>>();
        for (String var : varDomainTypes.keySet()) {
            HashSet<String> types = new HashSet();
            HashSet<String> domainTypes = varDomainTypes.get(var);
            HashSet<String> explicitTypes = varExplicitTypes.get(var);
            if (domainTypes != null)
                types.addAll(domainTypes);
            if (explicitTypes != null)
                types.addAll(explicitTypes);
            varmap.put(var, types);
        }
        for (String var : varExplicitTypes.keySet()) {
            HashSet<String> types = new HashSet();
            HashSet<String> domainTypes = varDomainTypes.get(var);
            HashSet<String> explicitTypes = varExplicitTypes.get(var);
            if (domainTypes != null)
                types.addAll(domainTypes);
            if (explicitTypes != null)
                types.addAll(explicitTypes);
            varmap.put(var, types);
        }
        return varmap;
    }

    /** ***************************************************************
     * Add clauses for every variable in the antecedent to restrict its
     * type to the type restrictions defined on every relation in which
     * it appears.  For example
     * (=>
     *   (foo ?A B)
     *   (bar B ?A))
     *
     * (domain foo 1 Z)
     *
     * would result in
     *
     * (=>
     *   (instance ?A Z)
     *   (=>
     *     (foo ?A B)
     *     (bar B ?A)))
     */
    public Formula addTypeRestrictions(Formula form, KB kb) {

        if (debug) System.out.println("addTypeRestrictions: form " + form);
        // get variable types from domain definitions
        HashMap<String,HashSet<String>> varmap = findTypeRestrictions(form,kb);

        // compute quantifiedVariables and unquantifiedVariables
        ArrayList<ArrayList<String>> quantifiedUnquantifiedVariables =
                form.collectQuantifiedUnquantifiedVariables();
        ArrayList<String> unquantifiedVariables = quantifiedUnquantifiedVariables.get(1);
        if (hasFormulaType(form,varmap))
            form.higherOrder = true;
        // add sortals for unquantifiedVariables
        StringBuffer sb = new StringBuffer();
        boolean begin = true;
        for (int i = 0; i < unquantifiedVariables.size(); i++) {
            String unquantifiedV = unquantifiedVariables.get(i);
            HashSet<String> types = varmap.get(unquantifiedV);
            if (types != null && !types.isEmpty()) {
                for (String t : types) {
                    if (begin) {
                        sb.append("(=> \n  (and \n");  // TODO: need test for singular list
                        begin = false;
                    }
                    if (!t.endsWith("+"))
                        sb.append(" (instance " + unquantifiedV + " " + t + ") ");
                    else
                        sb.append(" (subclass " + unquantifiedV + " " + t.substring(0,t.length()-1) + ") ");
                }
            }
        }

        if (!begin)
            sb.append(")\n");
        if (debug) System.out.println("addTypeRestrictions: sb: " + sb);
        // recursively add sortals for existentially quantified variables
       // if ((form.theFormula.indexOf(Formula.EQUANT) > -1) ||
       //         (form.theFormula.indexOf(Formula.UQUANT) > -1))
        addTypeRestrictionsRecurse(kb, form, sb);

        if (!begin)
            sb.append(")\n");

        Formula f = new Formula();
        f.read(sb.toString());

        if (StringUtil.emptyString(f.theFormula) || f.empty())
            f.read(form.theFormula);
        if (debug) System.out.println("addTypeRestrictions: result: " + f);
        if (debug) System.out.println("addTypeRestrictions: form at end: " + form);
        if (debug) System.out.println("addTypeRestrictions: sb at end: '" + sb + "'");
        return f;
    }

    /** ***************************************************************
     * Recursively add sortals for existentially quantified variables
     *
     * @param kb The KB used to add type restrictions.
     * @param f The formula in KIF syntax
     * @param sb A StringBuilder used to store the new formula with sortals
     */
    private void addTypeRestrictionsRecurse(KB kb, Formula f, StringBuffer sb) {

        if (debug) System.out.println("addTypeRestrictionsRecurse: input: " + f);
        if (debug) System.out.println("addTypeRestrictionsRecurse: sb: " + sb);
        if (f == null || StringUtil.emptyString(f.theFormula) || f.empty())
            return;

        String carstr = f.car();
        if (debug) System.out.println("addTypeRestrictionsRecurse: carstr: " + carstr);
        if (Formula.atom(carstr) && (Formula.isLogicalOperator(carstr) || carstr.equals(Formula.EQUAL))) {
            sb.append("(" + carstr + " ");
            if (debug) System.out.println("addTypeRestrictionsRecurse: interior sb: " + sb);
            if (carstr.equals(f.EQUANT) || carstr.equals(f.UQUANT)) {
                // If we see existentially quantified variables, like (exists (?X ?Y) ...),
                //   and if ?X, ?Y are not explicitly restricted in the following statements,
                // we need to add type restrictions for ?X, ?Y
                sb.append(f.getArgument(1) + " ");
                ArrayList<String> quantifiedVariables = collectVariables(f.getArgument(1));
                // set addSortals = true, if at least one variable is existentially quantified variable,
                // and it is not explicitly restricted
                boolean addSortals = false;

                HashMap<String,HashSet<String>> varDomainTypes = computeVariableTypes(f, kb);
                HashMap<String,HashSet<String>> varExplicitTypes = findExplicitTypesClassesInAntecedent(kb,f);

                // only keep variables which are not explicitly defined in formula
                HashMap<String,HashSet<String>> varmap = (HashMap<String, HashSet<String>>) varDomainTypes.clone();
                if (varExplicitTypes != null) {
                    for (String v : varExplicitTypes.keySet())
                        varmap.remove(v);
                }

                for (String ev : quantifiedVariables) {
                    HashSet<String> types = varmap.get(ev);
                    if (types != null && !types.isEmpty()) {
                            addSortals = true;
                            break;
                    }
                }
                if (addSortals) {
                    if (carstr.equals(f.EQUANT)) sb.append("(and ");
                    else if (carstr.equals(f.UQUANT)) sb.append("(=> (and ");
                }

                for (int i = 0; i < quantifiedVariables.size(); i++) {
                    String existentiallyQV = quantifiedVariables.get(i);
                    HashSet<String> types = varmap.get(existentiallyQV);
                    if (types != null && !types.isEmpty()) {
                        for (String t : types) {
                            if (!t.endsWith("+"))
                                sb.append(" (instance " + existentiallyQV + " " + t + ") ");
                            else
                                sb.append(" (subclass " + existentiallyQV + " " + t.substring(0,t.length()-1) + ") ");
                        }
                    }
                }
                if (addSortals && carstr.equals(f.UQUANT))
                    sb.append(")");
                for (int i = 2 ; i < f.listLength(); i++)
                    addTypeRestrictionsRecurse(kb, new Formula(f.getArgument(i)), sb);
                if (addSortals)
                    sb.append(")");
            }
            else {
                if (debug) System.out.println("addTypeRestrictionsRecurse: input interior: " + f);
                if (debug) System.out.println("addTypeRestrictionsRecurse: args: " + f.complexArgumentsToArrayList(1));
                if (debug) System.out.println("addTypeRestrictionsRecurse: list length: " + f.listLength());

                if (debug)
                    for (int i = 1; i < f.listLength(); i++) {
                        Formula newF = new Formula(f.getArgument(i));
                        System.out.println("addTypeRestrictionsRecurse: " + f.getArgument(i) + " : " + newF + " : " + newF.theFormula);
                    }
                // recurse from the first argument if the formula is not in (exists ...) / (forall ...) scope
                for (int i = 1; i < f.listLength(); i++)
                    addTypeRestrictionsRecurse(kb, new Formula(f.getArgument(i)), sb);
            }
            sb.append(")");
        }
        else if (f.isSimpleClause(kb) || f.atom()) {
            if (debug) System.out.println("addTypeRestrictionsRecurse: here2");
            sb.append(f + " ");
        }
        else {
            if (debug) System.out.println("addTypeRestrictionsRecurse: here3");
            addTypeRestrictionsRecurse(kb, f.carAsFormula(), sb);
            addTypeRestrictionsRecurse(kb, f.cdrAsFormula(), sb);
        }
    }

    /** ***************************************************************
     * Collect variables from strings.
     *
     * For example,
     * Input = (?X ?Y ?Z)
     * Output = a list of ?X, ?Y and ?Z
     *
     * Input = ?X
     * Output = a list of ?X
     */
    private ArrayList<String> collectVariables(String argstr) {

        ArrayList<String> arglist = new ArrayList<>();
        if (argstr.startsWith(Formula.V_PREF)) {
            arglist.add(argstr);
            return arglist;
        }
        else if (argstr.startsWith(Formula.LP)) {
            arglist = new ArrayList<>(Arrays.asList(argstr.substring(1, argstr.length()-1).split(" ")));
            return arglist;
        }
        else {
            System.err.println("Errors in FormulaPreprocessor.collectVariables ...");
            return null;
        }
    }

    /** ************************************************************************
     * Get the most specific type for variables.
     *
     * @param kb The KB to be used for processing
     * @param types a list of sumo types for a sumo term/variable
     * @return the most specific sumo type for the term/variable
     *
     * For example
     * types of ?Writing = [Entity, Physical, Process, IntentionalProcess,
     *                      ContentDevelopment, Writing]
     * return the most specific type Writing
     */
    protected String getMostRelevantType(KB kb, HashSet<String> types) {

        HashSet<String> insts = new HashSet<String>();
        Iterator<String> iter = types.iterator();
        while (iter.hasNext()) {
            String type = iter.next();
            if (!type.endsWith("+"))
                insts.add(type);
            else
                insts.add(type.substring(0, type.length()-1));
        }
        if (insts != null) {
            winnowTypeList(insts, kb);
            Iterator<String> it1 = insts.iterator();
            while (it1.hasNext()) {
                return it1.next();
            }
        }

        return null;
    }

    /*****************************************************************
     * Collect the types of any variables that are specifically defined
     * in the antecedent of a rule with an instance or subclass expression.
     * TODO: This may ultimately require CNF conversion and then checking negative
     * literals, but for now it's just a hack to grab preconditions.
     */
    public HashMap<String, HashSet<String>> findExplicitTypesInAntecedent(KB kb, Formula form) {

        if (!form.isRule())
            // TODO: Consider returning empty map instead of null. Check callers for special behavior on null.
            return null;

        Formula f = new Formula();
        f.read(form.theFormula);
        Formula antecedent = f.cdrAsFormula().carAsFormula();

        return findExplicitTypes(kb,antecedent);
    }

    /*****************************************************************
     * Collect the types of any variables that are specifically defined
     * in the antecedent of a rule with an instance expression;
     * Collect the super classes of any variables that are specifically
     * defined in the antecedent of a rule with an subclass expression;
     */
    public HashMap<String, HashSet<String>> findExplicitTypesClassesInAntecedent(KB kb, Formula form) {

        Formula f = new Formula();
        f.read(form.theFormula);
        Formula antecedent = findAntecedent(f);
        HashMap<String, HashSet<String>> varExplicitTypes = new HashMap<>();
        HashMap<String, HashSet<String>> varExplicitClasses = new HashMap<>();
        findExplicitTypesClasses(kb, antecedent, varExplicitTypes, varExplicitClasses);
        return varExplicitTypes;
    }

    /** ***************************************************************
     * Return a formula's antecedents
     */
    private static Formula findAntecedent(Formula f) {

        if (f.theFormula.indexOf(f.IF) == -1 && f.theFormula.indexOf(f.IFF) == -1)
            return f;
        String carstr = f.car();
        if (Formula.atom(carstr) && Formula.isLogicalOperator(carstr)) {
            if (carstr.equals(f.IF) || carstr.equals(f.IFF))
                return f.cdrAsFormula().carAsFormula();
            else
                return f;
        }
        return f;
    }

    /*****************************************************************
     * Collect variable names and their types from instance or subclass
     * expressions. subclass restrictions are marked with a '+'.
     *
     * @param form The formula in KIF syntax
     *
     * @return A map of variables paired with a set of sumo types collected
     * from instance and subclass expressions.
     *
     * TODO: This may ultimately require CNF conversion and then checking
     * negative literals, but for now it's just a hack to grab preconditions.
     */
    public HashMap<String, HashSet<String>> findExplicitTypes(KB kb, Formula form) {

        HashMap<String,HashSet<String>> varExplicitTypes = new HashMap<String,HashSet<String>>();
        HashMap<String,HashSet<String>> varExplicitClasses = new HashMap<String,HashSet<String>>();
        findExplicitTypesRecurse(kb, form, false, varExplicitTypes, varExplicitClasses);

        varExplicitTypes.putAll(varExplicitClasses);
        return varExplicitTypes;
    }

    /*****************************************************************
     * Collect variable names and their types from instance or subclass
     * expressions.
     *
     * @param form The formula in KIF syntax
     * @param varExplicitTypes A map of variables paired with sumo types
     *                         collected from instance expressions
     * @param varExplicitClasses A map of variables paired with sumo types
     *                           collected from subclass expression
     */
    public void findExplicitTypesClasses(KB kb, Formula form,
           HashMap<String,HashSet<String>> varExplicitTypes,
           HashMap<String,HashSet<String>> varExplicitClasses) {

        findExplicitTypesRecurse(kb, form, false, varExplicitTypes, varExplicitClasses);
    }

    /*****************************************************************
     * Recursively collect a variable name and its types.
     */
    public static void findExplicitTypesRecurse(KB kb, Formula form, boolean isNegativeLiteral,
                 HashMap<String,HashSet<String>> varExplicitTypes,
                 HashMap<String, HashSet<String>> varExplicitClasses) {

        if (form == null || StringUtil.emptyString(form.theFormula) || form.empty())
            return;

        String carstr = form.car();

        if (Formula.atom(carstr) && Formula.isLogicalOperator(carstr)) {
            if (carstr.equals(form.EQUANT) || carstr.equals(form.UQUANT)) {
                for (int i = 2 ; i < form.listLength(); i++)  // (exists (?X ?Y) (foo1 ?X ?Y)), recurse from the second argument
                    findExplicitTypesRecurse(kb,new Formula(form.getArgument(i)), false, varExplicitTypes, varExplicitClasses);
            }
            else if (carstr.equals(form.NOT)) {
                for (int i = 1; i < form.listLength(); i++)   // (not (foo1 ?X ?Human)), set isNegativeLiteral = true, and recurse from the first argument
                    findExplicitTypesRecurse(kb,new Formula(form.getArgument(i)), true, varExplicitTypes, varExplicitClasses);
            }
            else {
                for (int i = 1; i < form.listLength(); i++)   // eg. (and (foo1 ?X ?Y) (foo2 ?X ?Z)), recurse from the first argument
                    findExplicitTypesRecurse(kb,new Formula(form.getArgument(i)), false, varExplicitTypes, varExplicitClasses);
            }
        }
        else if (form.isSimpleClause(kb)) {
            if (isNegativeLiteral == true)  // If form is negative literal, do not add explicit type for the variable
                return;
            Pattern p = Pattern.compile("\\(instance (\\?[a-zA-Z0-9\\-_]+) ([\\?a-zA-Z0-9\\-_]+)");
            Matcher m = p.matcher(form.theFormula);
            while (m.find()) {
                String var = m.group(1);
                String cl = m.group(2);
                HashSet<String> hs = new HashSet<>();
                if (!cl.startsWith("?")) {
                    if (varExplicitTypes.containsKey(var))
                        hs = varExplicitTypes.get(var);
                    hs.add(cl);
                }
                else {
                    if (varExplicitTypes.containsKey(var))
                        hs = varExplicitTypes.get(var);
                }
                if (hs != null && hs.size() != 0)
                    varExplicitTypes.put(var, hs);
            }

            p = Pattern.compile("\\(subclass (\\?[a-zA-Z0-9\\-_]+) ([\\?a-zA-Z0-9\\-]+)");
            m = p.matcher(form.theFormula);
            while (m.find()) {
                String var = m.group(1);
                String cl = m.group(2);
                HashSet<String> hs = new HashSet<>();
                if (!cl.startsWith("?")) {
                    if (varExplicitClasses.containsKey(var))
                        hs = varExplicitClasses.get(var);
                    hs.add(cl + "+");
                }
                else {
                    if (varExplicitClasses.containsKey(var))
                        hs = varExplicitClasses.get(var);
                }
                if (hs != null && hs.size() != 0)
                    varExplicitClasses.put(var, hs);
            }
        }
        else {
            findExplicitTypesRecurse(kb,form.carAsFormula(), false, varExplicitTypes, varExplicitClasses);
            findExplicitTypesRecurse(kb,form.cdrAsFormula(), false, varExplicitTypes, varExplicitClasses);
        }
    }

    /** ***************************************************************
     * utility method to add a String element to a HashMap of String
     * keys and a value of an HashSet of Strings
     */
    public static void addToMap(HashMap<String,HashSet<String>> map, String key, String element) {

        HashSet<String> al = map.get(key);
        if (al == null)
            al = new HashSet<String>();
        al.add(element);
        map.put(key, al);
    }

    /** ***************************************************************
     * utility method to merge two HashMaps of String keys and a values
     * of an HashSet of Strings.  Note that parent classes in the set of
     * classes will be removed
     */
    static HashMap<String, HashSet<String>> mergeToMap(HashMap<String, HashSet<String>> map1,
                                                       HashMap<String, HashSet<String>> map2, KB kb) {

        HashMap<String, HashSet<String>> result = new HashMap<String,HashSet<String>>(map1);

        for (String key : map2.keySet()) {
            Set<String> value = new HashSet<String>();
            if (result.containsKey(key)) {
                value = result.get(key);
            }
            value.addAll(map2.get(key));
            value = kb.removeSuperClasses(value);
            result.put(key, Sets.newHashSet(value));
        }
        return result;
    }

    /*****************************************************************
     * This method returns a HashMap that maps each String variable in
     * this the names of types (classes) of which the variable must be
     * an instance or the names of types of which the variable must be
     * a subclass. Note that this method does not capture explicit type
     * from assertions such as (=> (instance ?Foo Bar) ...). This method
     * just consider restrictions implicitly defined from the arg types
     * of relations.
     *
     * @param kb The KB to be used to compute the sortal constraints
     *           for each variable.
     * @return A HashMap of variable names and their types. Subclass
     *         restrictions are marked with a '+', meaning that a
     *         domainSubclass is defined for this argument in one of
     *         the loaded .kif files. Instance restrictions have no
     *         special mark.
     */
    public HashMap<String,HashSet<String>> computeVariableTypes(Formula form, KB kb) {

        if (debug) System.out.println("INFO in FormulaPreprocessor.computeVariableTypes(): \n" + form);
        Formula f = new Formula();
        f.read(form.theFormula);
        HashMap<String,HashSet<String>> result = new HashMap<String,HashSet<String>>();
        return computeVariableTypesRecurse(kb,form,result);
    }

    /** ***************************************************************
     */
    private HashMap<String,HashSet<String>> computeVariableTypesRecurse(KB kb, Formula f,
                                                                       HashMap<String,HashSet<String>> input) {

        HashMap<String,HashSet<String>> result = new HashMap<String,HashSet<String>>();
        if (f == null || StringUtil.emptyString(f.theFormula) || f.empty())
            return result;
        if (debug) System.out.println("INFO in FormulaPreprocessor.computeVariableTypesRecurse(): \n" + f);
        String carstr = f.car();
        if (Formula.atom(carstr) && Formula.isLogicalOperator(carstr)) {// equals may require special treatment
            result.putAll(input);
            for (int i = 1; i < f.listLength(); i++)
                result = mergeToMap(result,computeVariableTypesRecurse(kb,new Formula(f.getArgument(i)),input), kb);
        }
        else if (f.isSimpleClause(kb)) { // simple clauses include functions
            String pred = carstr;
            if (f.theFormula.indexOf("?") > -1 && !Formula.isVariable(pred)) {
                Formula newf = f.cdrAsFormula();
                if (pred.equals(Formula.EQUAL)) {
                    ArrayList<String> args = f.complexArgumentsToArrayList(1);
                    if (Formula.isVariable(args.get(0)) && Formula.listP(args.get(1)) &&
                            kb.isFunctional(args.get(1))) {
                        Formula func = new Formula(args.get(1));
                        String fstr = func.car();
                        String type = kb.kbCache.getRange(fstr);
                        addToMap(result,args.get(0),type);
                    }
                    if (Formula.isVariable(args.get(1)) && Formula.listP(args.get(0)) &&
                            kb.isFunctional(args.get(0))) {
                        Formula func = new Formula(args.get(0));
                        String fstr = func.car();
                        String type = kb.kbCache.getRange(fstr);
                        addToMap(result,args.get(1),type);
                    }
                }

                int argnum = 1;
                while (!newf.empty()) {
                    String arg = newf.car();
                    if (debug)
                        System.out.println("arg,pred,argnum: " + arg + ", " + pred + ", " + argnum);
                    if (debug)
                        System.out.println("newf: " + newf);
                    if (debug)
                        System.out.println("is function?: " + kb.isFunctional(arg));
                    if (Formula.isVariable(arg)) {
                        String cl = findType(argnum, pred, kb);
                        if (debug)
                            System.out.println("cl: " + cl);
                        if (StringUtil.emptyString(cl)) {
                            if (kb.kbCache == null || !kb.kbCache.transInstOf(pred, "VariableArityRelation"))
                                System.out.println("Error in FormulaPreprocessor.computeVariableTypesRecurse(): " +
                                        "no type information for arg " + argnum + " of relation " + pred + " in formula: \n" + f);
                        }
                        else
                            addToMap(result, arg, cl);
                    }
                    // If formula is function then recurse.
                    else if (Formula.listP(arg) && kb.isFunctional(arg)) {
                        if (debug)
                            System.out.println("arg is a function: " + arg);
                        result = mergeToMap(result, computeVariableTypesRecurse(kb, new Formula(arg), input), kb);
                    }
                    newf = newf.cdrAsFormula();
                    argnum++;  // note that this will try an argument that doesn't exist, and terminate when it does
                }

            }
        }
        else {
            result = mergeToMap(input,computeVariableTypesRecurse(kb,f.carAsFormula(),input), kb);
            result = mergeToMap(result,computeVariableTypesRecurse(kb,f.cdrAsFormula(),input), kb);
        }
        if (debug)System.out.println("INFO in FormulaPreprocessor.computeVariableTypesRecurse(): exiting from\n" + f);
        if (debug)System.out.println("INFO in FormulaPreprocessor.computeVariableTypesRecurse(): with result:" + result);
        return result;
    }

    /** ***************************************************************
     * Pre-process a formula before sending it to the theorem prover.
     * This includes ignoring meta-knowledge like documentation strings,
     * translating mathematical operators, quoting higher-order formulas,
     * adding a numerical suffix to VariableArityRelations based on their count,
     * expanding row variables and prepending the 'holds__' predicate.
     * @return an ArrayList of Formula(s)
     */
    private String preProcessRecurse(Formula f, String previousPred, boolean ignoreStrings,
                                     boolean translateIneq, boolean translateMath,
                                     KB kb) {


        //if (debug) System.out.println("preProcessRecurse: " + f);
        StringBuilder result = new StringBuilder();
        if (f.listP() && !f.empty()) {
            String prefix = "";
            String pred = f.car();
            if (Formula.isQuantifier(pred)) {
                // The list of quantified variables.
                result.append(" ");
                result.append(f.cadr());
                // The formula following the list of variables.
                String next = f.caddr();
                Formula nextF = new Formula();
                nextF.read(next);
                result.append(" ");
                result.append(preProcessRecurse(nextF,"",ignoreStrings,translateIneq,translateMath,kb));
            }
            else {
                if (kb.isInstanceOf(pred,"VariableArityRelation")) {
                    int arity = f.complexArgumentsToArrayList(0).size()-1;
                    String oldPred = pred;

                    // note this has to match with Formula.renameVariableArityRelations()
                   // if (pred.endsWith("Fn"))
                   //     pred = pred.substring(0,pred.length()-2) + "_" + arity + "_Fn";
                    //else
                        pred = pred + "_" + arity;
                    kb.kbCache.copyNewPredFromVariableArity(pred,oldPred,arity);
                    if (debug) System.out.println("preProcessRecurse: pred: " + pred);
                }
                Formula restF = f.cdrAsFormula();
                //if (debug) System.out.println("preProcessRecurse: restF: " + restF);
                int argCount = 1;
                while (!restF.empty()) {
                    argCount++;
                    String arg = restF.car();
                    Formula argF = new Formula();
                    argF.read(arg);
                    if (argF.listP()) {
                        String res = preProcessRecurse(argF,pred,ignoreStrings,translateIneq,translateMath,kb);
                        result.append(" ");
                        /* if (!Formula.isLogicalOperator(pred) &&
                                !Formula.isComparisonOperator(pred) &&
                                !Formula.isMathFunction(pred) &&
                                !kb.isFunctional(argF.theFormula)) {
                            result.append("`");
                        } */
                        result.append(res);
                    }
                    else
                        result.append(" " + arg);
                    restF.theFormula = restF.cdr();
                    //if (debug) System.out.println("preProcessRecurse: restF: " + restF);
                    //if (debug) System.out.println("preProcessRecurse: result: " + result);
                }
                if (KBmanager.getMgr().getPref("holdsPrefix").equals("yes")) {
                    if (!Formula.isLogicalOperator(pred) && !Formula.isQuantifierList(pred,previousPred))
                        prefix = "holds_";
                    if (kb.isFunctional(f.theFormula))
                        prefix = "apply_";
                    if (pred.equals("holds")) {
                        pred = "";
                        argCount--;
                        prefix = prefix + argCount + "__ ";
                    }
                    else {
                        if (!Formula.isLogicalOperator(pred) &&
                                !Formula.isQuantifierList(pred,previousPred) &&
                                !Formula.isMathFunction(pred) &&
                                !Formula.isComparisonOperator(pred)) {
                            prefix = prefix + argCount + "__ ";
                        }
                        else
                            prefix = "";
                    }
                }
            }
            result.insert(0, pred);
            result.insert(0, prefix);
            result.insert(0, "(");
            result.append(")");
            //if (debug) System.out.println("preProcessRecurse: result: " + result);
        }
        return result.toString();
    }

    /** ***************************************************************
     * Tries to successively instantiate predicate variables and then
     * expand row variables in this Formula, looping until no new
     * Formulae are generated.
     *
     * @param kb The KB to be used for processing this Formula
     *
     * @param addHoldsPrefix If true, predicate variables are not
     * instantiated
     *
     * @return an ArrayList of Formula(s), which could be empty.
     */
    private ArrayList<Formula> replacePredVarsAndRowVars(Formula form, KB kb, boolean addHoldsPrefix) {

        ArrayList<Formula> result = new ArrayList<Formula>();
        //if (debug) System.out.println("replacePredVarsAndRowVars: " + form);
        Formula startF = new Formula();
        startF.read(form.theFormula);
        LinkedHashSet<Formula> accumulator = new LinkedHashSet<Formula>();
        accumulator.add(startF);
        ArrayList<Formula> working = new ArrayList<Formula>();
        int prevAccumulatorSize = 0;
        Formula f = null;
        while (accumulator.size() != prevAccumulatorSize) {
            prevAccumulatorSize = accumulator.size();
            // Initialize predicate variables if we are not adding holds prefixes.
            if (!addHoldsPrefix) {
                working.clear();
                working.addAll(accumulator);
                accumulator.clear();
                Iterator<Formula> it = working.iterator();
                while (it.hasNext()) {
                    f = (Formula) it.next();
                    Set<Formula> instantiations = PredVarInst.instantiatePredVars(f,kb);
                    if (debug) System.out.println("preProcess(): pred vars repl: " + f + "\n" + instantiations);
                    form.errors.addAll(f.getErrors());

                    // If the accumulator is null -- the formula can't be instantiated at all and has been marked "reject",
                    //    don't add anything
                    // If the accumulator is empty -- no pred var instantiations were possible,
                    //    add the original formula to the accumulator for possible row var expansion below.
                    if (instantiations != null) {
                        if (instantiations.isEmpty()) {
                            accumulator.add(f);
                        }
                        else {
                            accumulator.addAll(instantiations);
                        }
                    }
                }
            }
            // Row var expansion. Iterate over the instantiated predicate formulas,
            // doing row var expansion on each.  If no predicate instantiations can be generated, the accumulator
            // will contain just the original input formula.
            if (!accumulator.isEmpty() && (accumulator.size() < AXIOM_EXPANSION_LIMIT)) {
                working.clear();
                working.addAll(accumulator);
                accumulator.clear();
                Iterator<Formula> it2 = working.iterator();
                while (it2.hasNext()) {
                    f = (Formula) it2.next();
                    RowVars rv = new RowVars();
                    accumulator.addAll(RowVars.expandRowVars(kb,f));
                    if (accumulator.size() > AXIOM_EXPANSION_LIMIT) {
                        System.out.println("  AXIOM_EXPANSION_LIMIT EXCEEDED: " + AXIOM_EXPANSION_LIMIT);
                        break;
                    }
                }
            }
        }
        result.addAll(accumulator);
        //if (debug) System.out.println("replacePredVarsAndRowVars: result: " + result);
        return result;
    }

    /** ***************************************************************
     * Returns true if this Formula appears not to have any of the
     * characteristics that would cause it to be rejected during
     * translation to TPTP form, or cause problems during inference.
     * Otherwise, returns false.
     *
     * @param query true if this Formula represents a query, else
     * false.
     *
     * @param kb The KB object to be used for evaluating the
     * suitability of this Formula.
     *
     * @return boolean
     */
    private static boolean isOkForInference(Formula f, boolean query, KB kb) {

        boolean pass = false;
        // kb isn't used yet, because the checks below are purely
        // syntactic.  But it probably will be used in the future.
        pass = !(
                StringUtil.containsNonAsciiChars(f.theFormula)
                        // (<relation> ?X ...) - no free variables in an
                        // atomic formula that doesn't contain a string
                        // unless the formula is a query.
                        || (!query
                        && !Formula.isLogicalOperator(f.car())
                        // The formula does not contain a string.
                        && (f.theFormula.indexOf('"') == -1)
                        // The formula contains a free variable.
                        && f.theFormula.matches(".*\\?\\w+.*"))

                        // ... add more patterns here, as needed.
                        || false
        );
        return pass;
    }

    /** ***************************************************************
     * Adds statements of the form (instance <Entity> <SetOrClass>) if
     * they are not already in the KB.
     *
     * @param kb The KB to be used for processing the input Formulae
     * in variableReplacements
     *
     * @param isQuery If true, this method just returns the initial
     * input List, variableReplacements, with no additions
     *
     * @param variableReplacements A List of Formulae in which
     * predicate variables and row variables have already been
     * replaced, and to which (instance <Entity> <SetOrClass>)
     * Formulae might be added
     *
     * @return an ArrayList of Formula(s), which could be larger than
     * the input List, variableReplacements, or could be empty.
     */
    private ArrayList<Formula> addInstancesOfSetOrClass(Formula form, KB kb,
                               boolean isQuery, ArrayList<Formula> variableReplacements) {

        ArrayList<Formula> result = new ArrayList<Formula>();
        if ((variableReplacements != null) && !variableReplacements.isEmpty()) {
            if (isQuery)
                result.addAll(variableReplacements);
            else {
                HashSet<Formula> formulae = new HashSet<Formula>();
                String arg0 = null;
                Formula f = null;
                for (Iterator<Formula> it = variableReplacements.iterator(); it.hasNext();) {
                    f = it.next();
                    formulae.add(f);
                    if (f.listP() && !f.empty()) {  // Make sure every SetOrClass is stated to be such
                        arg0 = f.car();
                        int start = -1;
                        if (arg0.equals("subclass")) start = 0;
                        else if (arg0.equals("instance")) start = 1;
                        if (start > -1) {
                            ArrayList<String> args =
                                    new ArrayList<String>(Arrays.asList(f.getArgument(1),f.getArgument(2)));
                            int argslen = args.size();
                            String ioStr = null;
                            Formula ioF = null;
                            String arg = null;
                            for (int i = start; i < argslen; i++) {
                                arg = args.get(i);
                                if (!Formula.isVariable(arg) && !arg.equals("SetOrClass") && Formula.atom(arg)) {
                                    StringBuilder sb = new StringBuilder();
                                    sb.setLength(0);
                                    sb.append("(instance ");
                                    sb.append(arg);
                                    sb.append(" SetOrClass)");
                                    ioF = new Formula();
                                    ioStr = sb.toString().intern();
                                    ioF.read(ioStr);
                                    ioF.sourceFile = form.sourceFile;
                                    if (!kb.formulaMap.containsKey(ioStr)) {
                                        formulae.add(ioF);
                                    }
                                }
                            }
                        }
                    }
                }
                result.addAll(formulae);
            }
        }
        return result;
    }

    /** ***************************************************************
     * Pre-process a formula before sending it to the theorem prover.
     * This includes ignoring meta-knowledge like documentation strings,
     * translating mathematical operators, quoting higher-order formulas,
     * expanding row variables and prepending the 'holds__' predicate.
     *
     * @param isQuery If true the Formula is a query and should be
     *                existentially quantified, else the Formula is a
     *                statement and should be universally quantified
     *
     * @param kb The KB to be used for processing this Formula
     *
     * @return an Set of Formula(s), which could be empty.
     *
     */
    public Set<Formula> preProcess(Formula form, boolean isQuery, KB kb) {

        if (debug) System.out.println("preProcess(): starting on: " + form);
        HashSet<Formula> results = new HashSet<Formula>();
        if (!StringUtil.emptyString(form.theFormula)) {
            KBmanager mgr = KBmanager.getMgr();
            if (!form.isBalancedList()) {
                String errStr = "Unbalanced parentheses or quotes in: " + form.theFormula;
                form.errors.add(errStr);
                return results;
            }
            boolean ignoreStrings = false;
            boolean translateIneq = true;
            boolean translateMath = true;
            Formula f = new Formula();
            f.read(form.theFormula);
            if (StringUtil.containsNonAsciiChars(f.theFormula))
                f.theFormula = StringUtil.replaceNonAsciiChars(f.theFormula);

            boolean addHoldsPrefix = mgr.getPref("holdsPrefix").equalsIgnoreCase("yes");
            ArrayList<Formula> variableReplacements = replacePredVarsAndRowVars(form,kb, addHoldsPrefix);
            form.errors.addAll(f.getErrors());

            ArrayList<Formula> accumulator = addInstancesOfSetOrClass(form,kb, isQuery, variableReplacements);
            // Iterate over the formulae resulting from predicate variable instantiation and row variable expansion,
            // passing each to preProcessRecurse for further processing.
            if (!accumulator.isEmpty()) {
                Formula fnew = null;
                String theNewFormula = null;
                Iterator<Formula> it = accumulator.iterator();
                while (it.hasNext()) {
                    fnew = (Formula) it.next();
                    FormulaPreprocessor fp = new FormulaPreprocessor();
                    theNewFormula = fp.preProcessRecurse(fnew,"",ignoreStrings,translateIneq,translateMath,kb);
                    fnew.read(theNewFormula);
                    //if (debug) System.out.println("preProcess: fnew: " + fnew);
                    form.errors.addAll(fnew.getErrors());
                    fnew.sourceFile = form.sourceFile;
                    if (!StringUtil.emptyString(theNewFormula))
                        results.add(fnew);
                    if (debug) System.out.println("preProcess: results: " + results);
                }
            }
        }
        if (debug) System.out.println("INFO in FormulaPreprocessor.preProcess(): 1 result: " + results);

        // If typePrefix==yes and isQuery==false, add a "sortal" antecedent to every axiom
        KBmanager mgr = KBmanager.getMgr();
        boolean typePrefix = mgr.getPref("typePrefix").equalsIgnoreCase("yes");
        if (typePrefix && !isQuery) {
            Iterator<Formula> it = results.iterator();
            while (it.hasNext()) {
                Formula f = it.next();
                FormulaPreprocessor fp = new FormulaPreprocessor();
                Formula fnew = f;
                if (addTypes)
                    fp.addTypeRestrictions(f,kb);
                f.read(fnew.theFormula);
                f.higherOrder = fnew.higherOrder;
            }
        }

        if (debug) System.out.println("INFO in FormulaPreprocessor.preProcess(): 2 result: " + results);
        return results;
    }

    /** ***************************************************************
     */
    public static void testFindTypes() {

        System.out.println("------------------------------------");
        KBmanager.getMgr().initializeOnce();
        KB kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));

        System.out.println();
        String strf = "(=> (forall (?ELEMENT) (<=> (element ?ELEMENT ?SET1) " +
                "(element ?ELEMENT ?SET2))) (equal ?SET1 ?SET2))";
        Formula f = new Formula();
        f.read(strf);
        FormulaPreprocessor fp = new FormulaPreprocessor();
        System.out.println("Formula: " + f);
        System.out.println("Var types: " + fp.computeVariableTypes(f,kb));

        System.out.println();
        strf = "(=> (and (attribute ?AREA LowTerrain) (part ?ZONE ?AREA)" +
                " (slopeGradient ?ZONE ?SLOPE)) (greaterThan 0.03 ?SLOPE))";
        f.read(strf);
        fp = new FormulaPreprocessor();
        System.out.println("Formula: " + f);
        System.out.println("Var types: " + fp.computeVariableTypes(f,kb));

        System.out.println();
        strf = "(=> (and (typicalPart ?PART ?WHOLE) (instance ?X ?PART) " +
                "(equal ?PARTPROB (ProbabilityFn (exists (?Y) (and " +
                "(instance ?Y ?WHOLE) (part ?X ?Y))))) (equal ?NOTPARTPROB " +
                "(ProbabilityFn (not (exists (?Z) (and (instance ?Z ?WHOLE) " +
                "(part ?X ?Z))))))) (greaterThan ?PARTPROB ?NOTPARTPROB))";
        f.read(strf);
        fp = new FormulaPreprocessor();
        System.out.println("Formula: " + f);
        System.out.println("Var types: " + fp.computeVariableTypes(f,kb));

        System.out.println();
        strf = "(<=> (instance ?REL TransitiveRelation) " +
                "(forall (?INST1 ?INST2 ?INST3) " +
                "(=> (and (?REL ?INST1 ?INST2) " +
                "(?REL ?INST2 ?INST3)) (?REL ?INST1 ?INST3))))";
        f.read(strf);
        fp = new FormulaPreprocessor();
        System.out.println("Formula: " + f);
        System.out.println("Var types: " + fp.computeVariableTypes(f,kb));
        System.out.println("Explicit types: " + fp.findExplicitTypesInAntecedent(kb,f));
    }

    /** ***************************************************************
     */
    public static void testFindExplicit() {

        System.out.println("------------------------------------");
        KBmanager.getMgr().initializeOnce();
        KB kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));
        String formStr = "(<=> (instance ?REL TransitiveRelation) " +
                "(forall (?INST1 ?INST2 ?INST3) " +
                "(=> (and (?REL ?INST1 ?INST2) " +
                "(?REL ?INST2 ?INST3)) (?REL ?INST1 ?INST3))))";
        Formula f = new Formula(formStr);
        FormulaPreprocessor fp = new FormulaPreprocessor();
        System.out.println("Formula: " + f);
        Pattern p = Pattern.compile("\\(instance (\\?[a-zA-Z0-9]+) ([a-zA-Z0-9\\-_]+)");
        Matcher m = p.matcher(formStr);
        m.find();
        String var = m.group(1);
        String cl = m.group(2);
        System.out.println("FormulaPreprocessor.testExplicit(): " + var + " " + cl);
        System.out.println("Explicit types: " + fp.findExplicitTypesInAntecedent(kb, f));
    }

    /** ***************************************************************
     */
    public static void testAddTypes() {

        System.out.println("------------------------------------");
        KBmanager.getMgr().initializeOnce();
        KB kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));

        System.out.println();
        String strf = "(=> (forall (?ELEMENT) (<=> (element ?ELEMENT ?SET1) " +
                "(element ?ELEMENT ?SET2))) (equal ?SET1 ?SET2))";
        Formula f = new Formula();
        f.read(strf);
        FormulaPreprocessor fp = new FormulaPreprocessor();
        FormulaPreprocessor.debug = true;
        System.out.println(fp.addTypeRestrictions(f,kb));

        System.out.println();
        strf = "(=> (and (attribute ?AREA LowTerrain) (part ?ZONE ?AREA)" +
                " (slopeGradient ?ZONE ?SLOPE)) (greaterThan 0.03 ?SLOPE))";
        f.read(strf);
        fp = new FormulaPreprocessor();
        System.out.println(fp.addTypeRestrictions(f,kb));

        System.out.println();
        strf = "(=> (and (typicalPart ?PART ?WHOLE) (instance ?X ?PART) " +
                "(equal ?PARTPROB (ProbabilityFn (exists (?Y) (and " +
                "(instance ?Y ?WHOLE) (part ?X ?Y))))) (equal ?NOTPARTPROB " +
                "(ProbabilityFn (not (exists (?Z) (and (instance ?Z ?WHOLE) " +
                "(part ?X ?Z))))))) (greaterThan ?PARTPROB ?NOTPARTPROB))";
        f.read(strf);
        fp = new FormulaPreprocessor();
        System.out.println(fp.addTypeRestrictions(f,kb));
    }

    /** ***************************************************************
     */
    public static void testOne() {

        System.out.println("------------------------------------");
        KBmanager.getMgr().initializeOnce();
        KB kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));

        System.out.println();
        System.out.println();
        FormulaPreprocessor fp = new FormulaPreprocessor();
        String strf = "(=>\n" +
                "    (equal\n" +
                "        (GreatestCommonDivisorFn @ROW) ?NUMBER)\n" +
                "    (forall (?ELEMENT)\n" +
                "        (=>\n" +
                "            (inList ?ELEMENT\n" +
                "                (ListFn @ROW))\n" +
                "            (equal\n" +
                "                (RemainderFn ?ELEMENT ?NUMBER) 0))))";
        Formula f = new Formula();
        f.read(strf);
        fp = new FormulaPreprocessor();
        //System.out.println(fp.findType(1,"part",kb));
        System.out.println(fp.preProcess(f,false,kb));
    }

    /** ***************************************************************
     */
    public static void testTwo() {

        System.out.println("------------------------------------");
        KBmanager.getMgr().initializeOnce();
        KB kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));

        System.out.println();
        System.out.println();
        FormulaPreprocessor fp = new FormulaPreprocessor();
        String strf = "(equal (AbsoluteValueFn ?NUMBER1) 2)";
        Formula f = new Formula();
        f.read(strf);
        fp = new FormulaPreprocessor();
        System.out.println("testTwo(): equality: " + fp.preProcess(f,false,kb));
    }

    /** ***************************************************************
     */
    public static void testThree() {

        System.out.println("------------------------------------");
        KBmanager.getMgr().initializeOnce();
        KB kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));

        System.out.println();
        System.out.println();
        FormulaPreprocessor fp = new FormulaPreprocessor();
        String strf = "\n" +
                "(<=>\n" +
                "    (and\n" +
                "        (equal\n" +
                "            (AbsoluteValueFn ?NUMBER1) ?NUMBER2)\n" +
                "        (instance ?NUMBER1 RealNumber)\n" +
                "        (instance ?NUMBER2 RealNumber))\n" +
                "    (or\n" +
                "        (and\n" +
                "            (instance ?NUMBER1 NonnegativeRealNumber)\n" +
                "            (equal ?NUMBER1 ?NUMBER2))\n" +
                "        (and\n" +
                "            (instance ?NUMBER1 NegativeRealNumber)\n" +
                "            (equal ?NUMBER2\n" +
                "                (SubtractionFn 0 ?NUMBER1)))))";
        Formula f = new Formula();
        f.read(strf);
        fp = new FormulaPreprocessor();
        System.out.println("testThree(): " + fp.preProcess(f,false,kb));
    }

    /** ***************************************************************
     */
    public static void testFour() {

        System.out.println("------------------------------------");
        KBmanager.getMgr().initializeOnce();
        KB kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));

        System.out.println();
        System.out.println();
        FormulaPreprocessor fp = new FormulaPreprocessor();
        String strf = "(forall (?NUMBER ?ELEMENT ?CLASS)\n" +
                "        (=>\n" +
                "          (equal ?ELEMENT\n" +
                "            (ListOrderFn\n" +
                "              (ListFn_1 ?FOO) ?NUMBER))\n" +
                "          (instance ?ELEMENT ?CLASS)))";
        Formula f = new Formula();
        f.read(strf);
        fp = new FormulaPreprocessor();
        System.out.println("testFour() signature for ListFn: " + kb.kbCache.signatures.get("ListFn"));
        System.out.println("testFour() valence for ListFn: " + kb.kbCache.valences.get("ListFn"));
        System.out.println("testFour() signature for ListFn_1: " + kb.kbCache.signatures.get("ListFn_1"));
        System.out.println("testFour() valence for ListFn_1: " + kb.kbCache.valences.get("ListFn_1"));
        System.out.println("testFour(): " + fp.addTypeRestrictions(f,kb));
    }

    /** ***************************************************************
     */
    public static void testFive() {

        System.out.println("------------------------------------");
        KBmanager.getMgr().initializeOnce();
        KB kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));

        System.out.println();
        System.out.println();
        FormulaPreprocessor fp = new FormulaPreprocessor();
        String strf = "(equal (AdditionFn 1 2) ?X)";
        Formula f = new Formula();
        f.read(strf);
        fp = new FormulaPreprocessor();
        System.out.println("testFive(): equality: " + fp.preProcess(f,false,kb));
    }

    /** ***************************************************************
     */
    public static void main(String[] args) {

        //testOne();
        //testTwo();
        //testThree();
        //testFour();

        testFive();
        //testFindTypes();
        //testAddTypes();
        //testFindExplicit();
    }

}
