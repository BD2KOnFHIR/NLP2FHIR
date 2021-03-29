package edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.impl.*;
import org.apache.uima.jcas.tcas.Annotation;
import org.hl7.fhir.*;
import org.hl7.fhir.dstu3.model.Resource;

public interface ResourceProducers {
    IParser FHIRPARSER = FhirContext.forDstu3().newJsonParser().setPrettyPrint(true);
    CompositionResourceProducer COMPOSITION = new CompositionResourceProducer();
    ConditionResourceProducer CONDITION = new ConditionResourceProducer();
    ProcedureResourceProducer PROCEDURE = new ProcedureResourceProducer();
    FamilyMemberHistoryResourceProducer FAMILY_MEMBER_HISTORY = new FamilyMemberHistoryResourceProducer();
    MedicationStatementResourceProducer MEDICATION_STATEMENT = new MedicationStatementResourceProducer();
    MedicationResourceProducer MEDICATION = new MedicationResourceProducer();
    ObservationResourceProducer OBSERVATION = new ObservationResourceProducer();
    // Maps
    static Resource parseResourceFromCasAnn(String documentID, Annotation ann) {
        if (ann instanceof Condition) {
            return CONDITION.produce(documentID, ann);
        }
        if (ann instanceof Procedure) {
            return PROCEDURE.produce(documentID, ann);
        }
        if (ann instanceof FamilyMemberHistory) {
            return FAMILY_MEMBER_HISTORY.produce(documentID, ann);
        }
        if (ann instanceof Composition) {
            return COMPOSITION.produce(documentID, ann);
        }
        if (ann instanceof MedicationStatement) {
            return MEDICATION_STATEMENT.produce(documentID, ann);
        }
        if (ann instanceof Medication) {
            return MEDICATION.produce(documentID, ann);
        }
        if (ann instanceof Observation) {
            return OBSERVATION.produce(documentID, ann);
        }
        return null;
    }
}
