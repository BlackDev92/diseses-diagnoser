package pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.service;

import org.apache.commons.lang3.StringUtils;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.swrlapi.core.SWRLAPIOWLOntology;
import org.swrlapi.core.SWRLAPIRule;
import org.swrlapi.exceptions.SWRLBuiltInException;
import org.swrlapi.parser.SWRLParseException;
import pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.exception.CreateRuleException;
import pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.model.Entity;
import pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.model.rule.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RulesManager {

    private final SWRLAPIOWLOntology ruleOntology;

    RulesManager(SWRLAPIOWLOntology ruleOntology) {
        this.ruleOntology = ruleOntology;
    }

    public void addRule(Rule rule) throws CreateRuleException {
        try {
            ruleOntology.createSWRLRule(rule.getName(), rule.toString());
        } catch (SWRLParseException | SWRLBuiltInException e) {
            throw new CreateRuleException(rule, e);
        }
    }

    public void deleteRule(Rule rule) {
        ruleOntology.deleteSWRLRule(rule.getName());
    }

    public void deleteRules(Collection<Rule> rules) {
        rules.forEach(rule -> ruleOntology.deleteSWRLRule(rule.getName()));
    }

    Collection<Rule> loadRules(Map<String, Entity> classes,
                               Map<String, Entity> symptoms,
                               Map<String, Entity> diseases,
                               Map<String, Entity> tests,
                               Map<String, Entity> treatments,
                               Map<String, Entity> causes) {
        Collection<Rule> rules = new ArrayList<>();
        for (SWRLAPIRule swrlRule : ruleOntology.getSWRLRules()) {
            Rule rule = new Rule(swrlRule.getRuleName());
            for (SWRLAtom atom : swrlRule.getBody()) {
                AbstractAtom bodyAtom = parseSWRLAtom(atom, classes, symptoms, diseases, tests, treatments, causes);
                if (isDeclarationAtom(bodyAtom))
                    rule.addDeclarationAtom(bodyAtom);
                else
                    rule.addBodyAtom(bodyAtom);
            }
            for (SWRLAtom atom : swrlRule.getHead()) {
                rule.addHeadAtom(parseSWRLAtom(atom, classes, symptoms, diseases, tests, treatments, causes));
            }
            rules.add(rule);
        }
        return rules;
    }

    @SuppressWarnings("unchecked")
    private AbstractAtom parseSWRLAtom(SWRLAtom swrlAtom,
                                       Map<String, Entity> classes,
                                       Map<String, Entity> symptoms,
                                       Map<String, Entity> diseases,
                                       Map<String, Entity> tests,
                                       Map<String, Entity> treatments,
                                       Map<String, Entity> causes) {
        String str = swrlAtom.toString();
        Pattern atomPattern = Pattern
                .compile("^(?<atomType>\\p{Alpha}+)\\(<\\S+#(?<atomID>\\w+)> (?<atomArguments>.+)\\)$");
        Pattern argumentPattern = Pattern.compile(
                "((?<argumentType>\\p{Alpha}*)\\(?<\\S+#(?<argumentID>\\w+)>\\)?)|(\"(?<value>\\d+)\"\\^\\^xsd:(?<valueType>[a-z]+))");
        Matcher atomMatcher = atomPattern.matcher(str);
        if (atomMatcher.find()) {

            String atomType = atomMatcher.group("atomType");
            String atomID = atomMatcher.group("atomID");
            String atomArguments = atomMatcher.group("atomArguments");
            Matcher argumentMatcher = argumentPattern.matcher(atomArguments);

            // class declaration
            if (atomType.equals("ClassAtom")) {
                return parseClassAtom(atomID, argumentMatcher, classes, symptoms, diseases, tests, treatments, causes);
            } else if (atomType.equals("ObjectPropertyAtom") || atomType.equals("DataPropertyAtom")
                    || atomType.equals("BuiltInAtom")) { // property
                return parseTwoArgumentsAtom(atomType, atomID, argumentMatcher, symptoms, diseases, tests, treatments, causes);
            }
        }
        return null;
    }

    private AbstractAtom parseClassAtom(String atomID,
                                        Matcher argumentMatcher,
                                        Map<String, Entity> classes,
                                        Map<String, Entity> symptoms,
                                        Map<String, Entity> diseases,
                                        Map<String, Entity> tests,
                                        Map<String, Entity> treatments,
                                        Map<String, Entity> causes) {
        if (argumentMatcher.find()) {
            String argumentType = argumentMatcher.group("argumentType");
            String argumentID = argumentMatcher.group("argumentID");
            if (argumentType.equals("Variable"))
                return new ClassDeclarationAtom<>(classes.get(atomID), new Variable(argumentID));
            if (symptoms.containsKey(argumentID))
                return new ClassDeclarationAtom<>(classes.get(atomID), symptoms.get(argumentID));
            if (diseases.containsKey(argumentID))
                return new ClassDeclarationAtom<>(classes.get(atomID), diseases.get(argumentID));
            if (tests.containsKey(argumentID))
                return new ClassDeclarationAtom<>(classes.get(atomID), tests.get(argumentID));
            if (treatments.containsKey(argumentID))
                return new ClassDeclarationAtom<>(classes.get(atomID), treatments.get(argumentID));
            if (causes.containsKey(argumentID))
                return new ClassDeclarationAtom<>(classes.get(atomID), causes.get(argumentID));
        }
        return null;
    }

    private AbstractAtom parseTwoArgumentsAtom(String atomType,
                                               String atomID,
                                               Matcher argumentMatcher,
                                               Map<String, Entity> symptoms,
                                               Map<String, Entity> diseases,
                                               Map<String, Entity> tests,
                                               Map<String, Entity> treatments,
                                               Map<String, Entity> causes) {
        int i = 0;
        @SuppressWarnings("rawtypes")
        TwoArgumentsAtom atom;
        if (atomType.equals("BuiltInAtom"))
            atom = new TwoArgumentsAtom<>(atomID, "swrlb");
        else
            atom = new TwoArgumentsAtom<>(atomID);
        while (argumentMatcher.find()) {
            i += 1;
            String argumentType = argumentMatcher.group("argumentType");
            String argumentID = argumentMatcher.group("argumentID");
            String value = argumentMatcher.group("value");
            String valueType = argumentMatcher.group("valueType");

            if (argumentType != null && argumentID != null) {
                if (argumentType.equals("Variable")) {
                    setAtomArgument(atom, new Variable(argumentID), i);
                } else if (argumentType.equals("")) {
                    Entity entity = getEntityFromArgumentId(argumentID, symptoms, diseases, tests, treatments, causes);
                    setAtomArgument(atom, entity, i);
                }
            } else if (valueType != null && value != null && StringUtils.isNumeric(value)) {
                int intVal = Integer.parseInt(value);
                setAtomArgument(atom, intVal, i);
            }
        }
        if (i == 2 && atom.getArgument1() != null && atom.getArgument2() != null)
            return atom;
        else
            return null;
    }

    private <T> void setAtomArgument(TwoArgumentsAtom atom, T argument, int i) {
        switch (i) {
            case 1:
                atom.setArgument1(argument);
            case 2:
                atom.setArgument2(argument);
        }
    }

    private Entity getEntityFromArgumentId(String argumentID,
                                           Map<String, Entity> symptoms,
                                           Map<String, Entity> diseases,
                                           Map<String, Entity> tests,
                                           Map<String, Entity> treatments,
                                           Map<String, Entity> causes) {
        if (symptoms.containsKey(argumentID))
            return symptoms.get(argumentID);
        else if (diseases.containsKey(argumentID))
            return diseases.get(argumentID);
        else if (tests.containsKey(argumentID))
            return tests.get(argumentID);
        else if (treatments.containsKey(argumentID))
            return treatments.get(argumentID);
        else if (causes.containsKey(argumentID))
            return causes.get(argumentID);
        else
            return null;
    }

    private boolean isDeclarationAtom(AbstractAtom atom) {
        if (atom instanceof ClassDeclarationAtom) {
            return true;
        }
        if (atom instanceof TwoArgumentsAtom) {
            TwoArgumentsAtom twoArgumentsAtom = (TwoArgumentsAtom) atom;
            if (twoArgumentsAtom.getArgument1() instanceof Variable && twoArgumentsAtom.getArgument2() instanceof Variable) {
                return true;
            }
        }
        return false;
    }
}
