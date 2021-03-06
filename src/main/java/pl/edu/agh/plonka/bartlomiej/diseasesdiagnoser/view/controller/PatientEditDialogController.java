package pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.view.controller;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.model.Patient;
import pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.service.PatientsService;
import pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.utils.NameUtils;
import pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.view.ViewManager;

import static pl.edu.agh.plonka.bartlomiej.diseasesdiagnoser.utils.binding.ObservableResourceFactory.getTranslation;

/**
 * Dialog to edit details of a patient.
 *
 * @author Bartłomiej Płonka
 */
public class PatientEditDialogController {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @FXML
    private TextField firstNameField;
    @FXML
    private TextField lastNameField;
    @FXML
    private TextField ageField;
    @FXML
    private TextField heightField;
    @FXML
    private TextField weightField;
    @FXML
    private TextField placeOfResidence;

    private Stage dialogStage;
    private Patient patient;
    private boolean okClicked = false;
    private ViewManager viewManager;
    private PatientsService patientsService;

    public void init(ViewManager viewManager, Stage dialogStage, PatientsService patientsService) {
        this.viewManager = viewManager;
        this.dialogStage = dialogStage;
        this.patientsService = patientsService;
    }

    /**
     * Initializes the controller class. This method is automatically called
     * after the fxml file has been loaded.
     */
    @FXML
    private void initialize() {
    }

    /**
     * Sets the patient to be edited in the dialog.
     *
     * @param patient
     */
    public void setPatient(Patient patient) {
        this.patient = patient;

        firstNameField.setText(patient.getFirstName());
        lastNameField.setText(patient.getLastName());
        if (patient.getAge() > 0)
            ageField.setText(Integer.toString(patient.getAge()));
        if (patient.getHeight() > 0)
            heightField.setText(Integer.toString(patient.getHeight()));
        if (patient.getWeight() > 0)
            weightField.setText(Integer.toString(patient.getWeight()));
    }

    /**
     * Returns true if the user clicked OK, false otherwise.
     *
     * @return
     */
    public boolean isOkClicked() {
        return okClicked;
    }

    /**
     * Called when the user clicks ok.
     */
    @FXML
    private void handleOk() {
        if (isInputValid()) {
            patient.setFirstName(firstNameField.getText());
            patient.setLastName(lastNameField.getText());
            patient.setAge(Integer.parseInt(ageField.getText()));
            patient.setHeight(Integer.parseInt(heightField.getText()));
            patient.setWeight(Integer.parseInt(weightField.getText()));
            okClicked = true;
            dialogStage.close();
        }
    }

    /**
     * Called when the user clicks cancel.
     */
    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    /**
     * Validates the user input in the text fields.
     *
     * @return true if the input is valid
     */
    private boolean isInputValid() {
        String errorMessage = "";

        if (firstNameField.getText() == null || firstNameField.getText().length() == 0) {
            errorMessage += getTranslation("INVALID_FIRST_NAME") + '\n';
        }
        if (lastNameField.getText() == null || lastNameField.getText().length() == 0) {
            errorMessage += getTranslation("INVALID_LAST_NAME") + '\n';
        }
        if (ageField.getText() == null || ageField.getText().length() == 0) {
            errorMessage += getTranslation("INVALID_AGE") + '\n';
        } else {
            try {
                Integer.parseInt(ageField.getText());
            } catch (NumberFormatException e) {
                errorMessage += getTranslation("INVALID_AGE_REQUIRED_INTEGER") + '\n';
            }
        }
        if (heightField.getText() == null || heightField.getText().length() == 0) {
            errorMessage += getTranslation("INVALID_HEIGHT") + '\n';
        } else {
            try {
                Integer.parseInt(heightField.getText());
            } catch (NumberFormatException e) {
                errorMessage += getTranslation("INVALID_HEIGHT_REQUIRED_INTEGER") + '\n';
            }
        }
        if (weightField.getText() == null || weightField.getText().length() == 0) {
            errorMessage += getTranslation("INVALID_WEIGHT") + '\n';
        } else {
            try {
                Integer.parseInt(weightField.getText());
            } catch (NumberFormatException e) {
                errorMessage += getTranslation("INVALID_WEIGHT_REQUIRED_INTEGER") + '\n';
            }
        }

        if (errorMessage.length() == 0) {
            return true;
        } else {
            viewManager.errorDialog(getTranslation("ERROR_CREATING_PATIENT"), null, errorMessage);
            return false;
        }
    }
}