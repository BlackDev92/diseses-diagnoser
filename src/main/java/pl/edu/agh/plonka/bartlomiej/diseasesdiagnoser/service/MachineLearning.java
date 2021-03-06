package pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.service;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.exception.PartialStarCreationException;
import pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.model.Entity;
import pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.model.Patient;
import pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.model.rule.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import static java.lang.String.format;
import static pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.model.rule.Category.Predicate.*;
import static pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.model.rule.ComplexComparator.sortStar;
import static pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.utils.Constants.*;

public class MachineLearning {

    // 0 for restrictive, 1 for general
    private static final float epsilon = 0.5f;
    private final Logger LOG = LoggerFactory.getLogger(getClass());
    private OntologyWrapper ontology;

    public MachineLearning(OntologyWrapper ontology) {
        this.ontology = ontology;
    }

    public Collection<Rule> sequentialCovering(Set<Patient> trainingSet) throws Throwable {
        ExecutorService service = Executors.newCachedThreadPool();
        Collection<Callable<Collection<Rule>>> callables = prepareCallables(trainingSet);
        List<Future<Collection<Rule>>> futures = service.invokeAll(callables);
        return collectResults(futures);
    }

    private Collection<Rule> collectResults(List<Future<Collection<Rule>>> futures) throws Throwable {
        Collection<Rule> rules = new HashSet<>();
        for (Future<Collection<Rule>> future : futures) {
            try {
                rules.addAll(future.get());
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        }
        return simplifyRules(rules);
    }

    private Collection<Callable<Collection<Rule>>> prepareCallables(Set<Patient> trainingSet) {
        Collection<Callable<Collection<Rule>>> callables = new ArrayList<>();
        callables.addAll(prepareCallable(trainingSet, ontology.getDiseases().values(), HAS_DISEASE));
        callables.addAll(prepareCallable(trainingSet, ontology.getTests().values(), SHOULD_MAKE_TEST));
        callables.addAll(prepareCallable(trainingSet, ontology.getTreatments().values(), SHOULD_BE_TREATED_WITH));
        callables.addAll(prepareCallable(trainingSet, ontology.getCauses().values(), CAUSE_OF_DISEASE));
        return callables;
    }

    private Collection<Callable<Collection<Rule>>> prepareCallable(Set<Patient> trainingSet,
                                                                   Collection<Entity> entities,
                                                                   Category.Predicate categoryPredicate) {
        Collection<Callable<Collection<Rule>>> callables = new ArrayList<>();
        for (Entity entity : entities) {
            callables.add(() -> sequentialCovering(trainingSet, new Category(entity, categoryPredicate)));
        }
        return callables;
    }

    private Collection<Rule> sequentialCovering(Set<Patient> trainingSet, Category category) throws PartialStarCreationException {
        Collection<Rule> rules = new HashSet<>();
        Set<Patient> uncoveredSet = new HashSet<>(trainingSet);
        int ruleIdx = 1;
        while (assertPatientWithCategoryInSet(uncoveredSet, category)) {
            Complex complex = findComplex(trainingSet, uncoveredSet, category);
            removeCoveredExamples(uncoveredSet, complex);
            Rule rule = complex.generateRule(generateRuleName(category, ruleIdx++), category, ontology);
            rules.add(rule);
        }
        return rules;
    }

    private Complex findComplex(Set<Patient> trainingSet, Set<Patient> uncoveredSet, Category category) throws PartialStarCreationException {
        LOG.debug("findComplex");
        Star star = new Star();
        Patient positiveSeed = positiveSeed(trainingSet, uncoveredSet, category);
        Patient negativeSeed = negativeSeed(trainingSet, star, positiveSeed, category);
        while (positiveSeed != null && negativeSeed != null) {
            Collection<Complex> partialStar = partialStar(positiveSeed, negativeSeed);
            if (partialStar.isEmpty()) {
                LOG.debug("Partial star is empty");
                throw new PartialStarCreationException(positiveSeed, negativeSeed);
            }
            star.intersection(partialStar);
            star.deleteNarrowComplexes();
            sortStar(star, category, trainingSet);
            star.leaveFirstElements(5);
            negativeSeed = negativeSeed(trainingSet, star, positiveSeed, category);
        }
        return star.get(0);
    }

    private Patient positiveSeed(Set<Patient> trainingSet, Set<Patient> uncoveredSet, Category category) {
        LOG.debug("positiveSeed");
        if (uncoveredSet.isEmpty())
            return null;
        Set<Patient> coveredSet = Sets.difference(trainingSet, uncoveredSet);
        Set<Patient> categoryCoveredSet = new HashSet<>();
        for (Patient uncovered : uncoveredSet) {
            if (category.assertPatientInCategory(uncovered)) {
                calculateDistance(uncovered, coveredSet);
                categoryCoveredSet.add(uncovered);
            }
        }
        return Collections.max(categoryCoveredSet);
    }

    private Patient negativeSeed(Collection<Patient> trainingSet, Star star, Patient positiveSeed, Category category) {
        LOG.debug("negativeSeed");
        List<Patient> negativeSeeds = new ArrayList<>();
        for (Patient patient : trainingSet) {
            if (star.isPatientCovered(patient) && !category.assertPatientInCategory(patient)) {
                negativeSeeds.add(patient);
            }
        }
        if (negativeSeeds.isEmpty())
            return null;
        Set<Patient> positiveSeedSingleton = Collections.singleton(positiveSeed);
        for (Patient negativeSeed : negativeSeeds)
            calculateDistance(negativeSeed, positiveSeedSingleton);
        return Collections.min(negativeSeeds);
    }

    private void calculateDistance(Patient patient, Collection<Patient> otherPatients) {
        LOG.debug("calculateDistance");
        if (otherPatients.isEmpty()) {
            LOG.debug("No other patients. Set patient distance to 0");
            patient.setEvaluation(0);
            return;
        }
        int symptomDiff = 0;
        int negTestDiff = 0;
        int disDiff = 0;
        int ageDiff = 0;
        int heightDiff = 0;
        int weightDiff = 0;
        for (Patient otherPatient : otherPatients) {
            symptomDiff += Sets.symmetricDifference(new HashSet<>(patient.getSymptoms()), new HashSet<>(otherPatient.getSymptoms())).size();
            negTestDiff += Sets.symmetricDifference(new HashSet<>(patient.getNegativeTests()), new HashSet<>(otherPatient.getNegativeTests())).size();
            disDiff += Sets.symmetricDifference(new HashSet<>(patient.getPreviousDiseases()),
                    new HashSet<>(otherPatient.getPreviousDiseases())).size();
            if (patient.getAge() >= 0 && otherPatient.getAge() >= 0)
                ageDiff += Math.abs(patient.getAge() - otherPatient.getAge());
            if (patient.getHeight() >= 0 && otherPatient.getHeight() >= 0)
                heightDiff += Math.abs(patient.getHeight() - otherPatient.getHeight());
            if (patient.getWeight() >= 0 && otherPatient.getWeight() >= 0)
                weightDiff += Math.abs(patient.getWeight() - otherPatient.getWeight());
        }
        float symptomEv = (float) symptomDiff / (otherPatients.size() * ontology.getSymptoms().size());
        float negTestEv = (float) negTestDiff / (otherPatients.size() * ontology.getTests().size());
        float disEv = (float) disDiff / (otherPatients.size() * ontology.getDiseases().size());
        float ageEv = (float) ageDiff / (otherPatients.size() * (PATIENT_MAX_AGE - PATIENT_MIN_AGE));
        float heightEv = (float) heightDiff / (otherPatients.size() * (PATIENT_MAX_HEIGHT - PATIENT_MIN_HEIGHT));
        float weightEv = (float) weightDiff / (otherPatients.size() * (PATIENT_MAX_WEIGHT - PATIENT_MIN_WEIGHT));

        patient.setEvaluation(symptomEv + negTestEv + disEv + ageEv + heightEv + weightEv);
    }

    private Collection<Complex> partialStar(Patient positivePatient, Patient negativePatient) {
        Collection<Complex> resultComplexes = new ArrayList<>();
        resultComplexes.addAll(createComplexes(positivePatient.getSymptoms(), negativePatient.getSymptoms(), Complex::setSymptomSelector));
        resultComplexes.addAll(createComplexes(positivePatient.getNegativeTests(), negativePatient.getNegativeTests(), Complex::setNegativeTestsSelector));
        resultComplexes.addAll(createComplexes(positivePatient.getPreviousDiseases(), negativePatient.getPreviousDiseases(), Complex::setPreviousDiseasesSelector));

        Complex ageComplex = createLinearComplex(positivePatient.getAge(), negativePatient.getAge(), Complex::setAgeSelector);
        Complex heightComplex = createLinearComplex(positivePatient.getHeight(), negativePatient.getHeight(), Complex::setHeightSelector);
        Complex weightComplex = createLinearComplex(positivePatient.getWeight(), negativePatient.getWeight(), Complex::setWeightSelector);

        if (ageComplex != null) resultComplexes.add(ageComplex);
        if (heightComplex != null) resultComplexes.add(heightComplex);
        if (weightComplex != null) resultComplexes.add(weightComplex);

        return resultComplexes;
    }

    private void removeCoveredExamples(Collection<Patient> trainingSet, Complex complex) {
        LOG.debug("removeCoveredExamples");
        trainingSet.removeIf(complex::isPatientCovered);
    }

    private Collection<Complex> createComplexes(Collection<Entity> positiveEntities, Collection<Entity> negativeEntities,
                                                BiConsumer<Complex, NominalSelector<Entity>> complexSetter) {
        ArrayList<Complex> complexes = new ArrayList<>();
        if (!positiveEntities.isEmpty()) {
            for (Entity entity : positiveEntities) {
                if (!negativeEntities.contains(entity)) {
                    Complex complex = createComplex(entity, complexSetter);
                    complexes.add(complex);
                }
            }
        }
        return complexes;
    }

    private Complex createComplex(Entity entity, BiConsumer<Complex, NominalSelector<Entity>> complexSetter) {
        NominalSelector<Entity> selector = new NominalSelector<>();
        selector.add(entity);
        Complex complex = new Complex();
        complexSetter.accept(complex, selector);
        return complex;
    }

    private Complex createLinearComplex(int posVal, int negVal, BiConsumer<Complex, LinearSelector<Integer>> complexSetter) {
        LinearSelector<Integer> selector = createLinearSelector(posVal, negVal);
        if (selector != null) {
            Complex complex = new Complex();
            complexSetter.accept(complex, selector);
            return complex;
        }
        return null;
    }

    private LinearSelector createLinearSelector(int posValue, int negValue) {
        if (posValue >= 0 && negValue >= 0 && posValue != negValue) {
            int midValue = Math.round(posValue + (negValue - posValue) * epsilon);
            if (negValue < posValue) {
                if (midValue == negValue)
                    return LinearSelector.greaterThanSelector(midValue);
                else
                    return LinearSelector.atLeastSelector(midValue);
            } else {
                if (midValue == negValue)
                    return LinearSelector.lessThanSelector(midValue);
                else
                    return LinearSelector.atMostSelector(midValue);
            }
        }
        return null;
    }

    private boolean assertPatientWithCategoryInSet(Set<Patient> patientsSet, Category category) {
        return patientsSet.stream().anyMatch(category::assertPatientInCategory);
    }

    private String generateRuleName(Category category, int ruleIdx) {
        String predicate;
        switch (category.getPredicate()) {
            case CAUSE_OF_DISEASE:
                predicate = CAUSE_OF_DISEASE_PROPERTY;
                break;
            case SHOULD_BE_TREATED_WITH:
                predicate = SHOULD_BE_TREATED_WITH_PROPERTY;
                break;
            case SHOULD_MAKE_TEST:
                predicate = SHOULD_MAKE_TEST_PROPERTY;
                break;
            case HAS_DISEASE:
                predicate = HAS_DISEASE_PROPERTY;
                break;
            default:
                predicate = "unknownPredicate";
        }
        return format("%s_%s_%s_%d", GENERATED_RULE_PREFIX, predicate, category.getEntity(), ruleIdx);
    }

    private Collection<Rule> simplifyRules(Collection<Rule> rules) {
        List<Rule> rulesList = new ArrayList<>(rules);
        for (int i = 0; i < rulesList.size() - 1; i++) {
            Rule rule = rulesList.get(i);
            for (int j = i + 1; j < rulesList.size(); j++) {
                Rule referenceRule = rulesList.get(j);
                if (rule.getBodyAtoms().equals(referenceRule.getBodyAtoms())) {
                    rule.addHeadAtoms(referenceRule.getHeadAtoms());
                    rulesList.remove(referenceRule);
                }
            }
        }
        return rulesList;
    }

}
