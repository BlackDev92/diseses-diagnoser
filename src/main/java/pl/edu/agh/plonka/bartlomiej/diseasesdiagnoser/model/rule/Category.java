package pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.model.rule;

import org.springframework.util.Assert;
import pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.model.Entity;
import pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.model.Patient;

public class Category {

    public enum Predicate {
        HAS_DISEASE,
        SHOULD_MAKE_TEST,
        SHOULD_BE_TREATED_WITH,
        CAUSE_OF_DISEASE
    }

    private Entity entity;
    private Predicate predicate;

    public Category(Entity entity, Predicate predicate) {
        Assert.notNull(entity, "Entity required.");
        Assert.notNull(entity, "Predicate required.");

        this.entity = entity;
        this.predicate = predicate;
    }

    public boolean assertPatientInCategory(Patient patient) {
        switch (predicate) {
            case HAS_DISEASE:
                return patient.getDiseases().contains(entity);
            case CAUSE_OF_DISEASE:
                return patient.getCauses().contains(entity);
            case SHOULD_MAKE_TEST:
                return patient.getTests().contains(entity);
            case SHOULD_BE_TREATED_WITH:
                return patient.getTreatments().contains(entity);
            default:
                return false;
        }
    }

    public void setPatientCategory(Patient patient) {
        switch (predicate) {
            case HAS_DISEASE:
                patient.addDisease(entity);
                break;
            case CAUSE_OF_DISEASE:
                patient.addCause(entity);
                break;
            case SHOULD_MAKE_TEST:
                patient.addTest(entity);
                break;
            case SHOULD_BE_TREATED_WITH:
                patient.addTreatment(entity);
                break;
        }
    }

    public Entity getEntity() {
        return entity;
    }

    public Predicate getPredicate() {
        return predicate;
    }
}
