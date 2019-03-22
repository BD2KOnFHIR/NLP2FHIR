var sectionMap = {
    "10154-3": "Chief complaint Narrative - Reported",
    "10157-6": "History of family member diseases Narrative",
    "10160-0": "History of medication use Narrative",
    "10164-2": "History of present illness Narrative",
    "10183-2": "Hospital discharge medications Narrative",
    "10184-0": "Hospital discharge physical findings Narrative",
    "10187-3": "Review of systems Narrative - Reported",
    "10210-3": "Physical findings of General status Narrative",
    "10216-0": "Surgical operation note fluids Narrative",
    "10218-6": "Surgical operation note postoperative diagnosis Narrative",
    "10223-6": "Surgical operation note surgical procedure Narrative",
    "10830-8": "Deprecated Surgical operation note complications",
    "11329-0": "History general Narrative - Reported",
    "11348-0": "History of past illness Narrative",
    "11369-6": "History of immunization Narrative",
    "11450-4": "Problem list - Reported",
    "11493-4": "Hospital discharge studies summary Narrative",
    "11535-2": "Hospital discharge Dx Narrative",
    "11537-8": "Surgical drains Narrative",
    "18776-5": "Plan of treatment (narrative)",
    "18841-7": "Hospital consultations Document",
    "29299-5": "Reason for visit Narrative",
    "29545-1": "Physical findings Narrative",
    "29549-3": "Medication administered Narrative",
    "29554-3": "Procedure Narrative",
    "29762-2": "Social history Narrative",
    "30954-2": "Relevant diagnostic tests/laboratory data Narrative",
    "42344-2": "Discharge diet (narrative)",
    "42346-7": "Medications on admission (narrative)",
    "42348-3": "Advance directives (narrative)",
    "42349-1": "Reason for referral (narrative)",
    "46240-8": "History of hospitalizations+History of outpatient visits Narrative",
    "46241-6": "Hospital admission diagnosis Narrative - Reported",
    "46264-8": "History of medical device use",
    "47420-5": "Functional status assessment note",
    "47519-4": "History of Procedures Document",
    "48765-2": "Allergies and adverse reactions Document",
    "48768-6": "Payment sources Document",
    "51848-0": "Evaluation note",
    "55109-3": "Complications Document",
    "55122-6": "Surgical operation note implants Narrative",
    "59768-2": "Procedure indications [interpretation] Narrative",
    "59769-0": "Postprocedure diagnosis Narrative",
    "59770-8": "Procedure estimated blood loss Narrative",
    "59771-6": "Procedure implants Narrative",
    "59772-4": "Planned procedure Narrative",
    "59773-2": "Procedure specimens taken Narrative",
    "59775-7": "Procedure disposition Narrative",
    "59776-5": "Procedure findings Narrative",
    "61149-1": "Objective Narrative",
    "61150-9": "Subjective Narrative",
    "69730-0": "Instructions",
    "8648-8": "Hospital course Narrative",
    "8653-8": "Hospital Discharge instructions",
    "8716-3": "Vital signs"
};

function Section() {
    this.id = "10154-3";
    this.name = sectionMap[this.id];
    this.body = "";

    this.updateId = function ($id) {
        this.id = $id;
        this.name = sectionMap[$id];
    }
}

function Request() {
    this.sections = [];


    this.addSection = function () {
        this.sections.push(new Section())
    };

    this.removeSection = function ($sec) {
        this.sections.splice($sec, 1);
    };
}

var app = angular.module("NLP2FHIRAPP", []);
app.controller("NLP2FHIRCTRL", function ($scope, $http) {
    this.request = new Request();
    this.SECTIONS = sectionMap;

    this.generate = function () {
        for (var i = 0; i < this.request.sections.length; i++) {
            this.request.sections[i].updateId(this.request.sections[i].id);
        }
        $http.post("/submit", this.request).then(function (resp) {
            var data = JSON.stringify(resp.data);
            var blob = new Blob([data], {type: 'text/plain'})
            if (window.navigator && window.navigator.msSaveOrOpenBlob) {
                window.navigator.msSaveOrOpenBlob(blob, filename);
            } else {
                var e = document.createEvent('MouseEvents'),
                    a = document.createElement('a');
                a.download = "ResourceBundle.json";
                a.href = window.URL.createObjectURL(blob);
                a.dataset.downloadurl = ['text/json', a.download, a.href].join(':');
                e.initEvent('click', true, false, window, 0, 0, 0, 0, 0, false, false, false, false, 0, null);
                a.dispatchEvent(e);
                // window.URL.revokeObjectURL(url); // clean the url.createObjectURL resource
            }
        })
    }
});


