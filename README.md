# NLP2FHIR
NLP2FHIR: A FHIR-based Clinical Data Normalization Pipeline and Its Applications
![alt text](https://raw.githubusercontent.com/BD2KOnFHIR/NLP2FHIR/master/nlp2fhir-system-architecture.png "NLP2FHIR System Architecture")

## Prerequisites
The following binaries and all associated resources are required in the classpath for successful execution, and are required on the build path (declared as a system library in maven) for developers
* [MedTagger](https://github.com/OHNLPIR/MedTagger)
* [cTAKES](http://ctakes.apache.org/downloads.cgi)
* [MedTime](https://github.com/OHNLPIR/MedTime)
* [MedXN](https://github.com/OHNLP/MedXN)
* [UMLS VTS](https://github.com/OHNLPIR/UMLS_VTS)

In addition, you will need the following:
* The MRCONSO.RRF file from a copy of the UMLS (placed in ./UMLS)
* SNOMEDCT US Edition resource files (downloadable with a UMLS license, placed in ./SNOMEDCT_US)

## For Users
Download all prerequisites, and compile each module using Maven (mvn clean install). You should obtain an executable upon completion in ./NLP2FHIR-GUI/target

Copy this to your root directory and launch using
``java –cp ./resources;./lib;./NLP2FHIR-GUI-1.0-SNAPSHOT.jar edu.mayo.bsi.nlp2fhir.gui.GUI``

Simply select the correct options relevant to your use case via the GUI and you are set! Make sure to insert your UMLS username and password in the upper right hand corner!

## For Developers
NLP2FHIR is written as a UIMA pipeline. As such, it is compatible in a plug and play manner with other UIMA pipelines. To leverage this functionality, please refer to `edu.mayo.bsi.nlp2fhir.pipelines` package in the NLP2FHIRAnnotators module. 

UIMA-FIT functionality is wrapped by pipeline builders, which are grouped by functionality. To see an example of how these classes interact directly with UIMA-FIT, please refer to `edu.mayo.bsi.nlp2fhir.pipelines.resources.ResourcePipelineBuilder`. To see how these pipelines are called

To add new resources, implement the parser/appropriate pipeline to populate the FHIR typesystem equivalent, then implement a resource producer to the `edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.impl` package and add a reference to the `edu.mayo.bsi.nlp2fhir.postprocessors.cas2fhir.ResourceProducers` class. 

Finally, add the appropriate items to the aforementioned `edu.mayo.bsi.nlp2fhir.pipelines.resources.ResourcePipelineBuilder` and `edu.mayo.bsi.nlp2fhir.pipelines.serialization.SerializationPipelineBuilder` classes

To add this functionality to the GUI itself, add the appropriate options under the NLP2FHIR-GUI module

## Demo App in Smart App Gallary
[NLP2FHIR: A FHIR-based Clinical Data Normalization Pipeline](https://apps.smarthealthit.org/app/nlp2fhir-a-fhir-based-clinical-data-normalization-pipeline)

## Useful Links
### [NLP2FHIR on ONC Interoperability Proving Ground](https://www.healthit.gov/techlab/ipg/node/4/submission/2511)

### [AMIA/HL7 FHIR® Applications Showcase](https://www.amia.org/amia2018/special-call-app-submissions)
NLP2FHIR: A FHIR-based Clinical Data Normalization Pipeline and Its Application on Electronic Health Records (EHR)-Driven Phenotyping

## Publications
* Hong N, Wen A, Shen F, Sohn S, Liu S, Liu H, Jiang G. Integrating Structured and Unstructured EHR Data Using an FHIR-based Type System: A Case Study with Medication Data. AMIA Jt Summits Transl Sci Proc. 2018 May 18;2017:74-83. [PubMED](https://www.ncbi.nlm.nih.gov/pubmed/29888045)

* Hong N, Wen A, Mojarad RM, Sohn S, Liu H, Jiang G. Standardizing Heterogeneous Annotation Corpora Using HL7 FHIR for Facilitating their Reuse and Integration in Clinical NLP. AMIA Annu Symp Proc 2018. (paper in press). [AMIA2018](https://symposium2018.zerista.com/event/member/508540)|[PubMed](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6371380/)

* Hong N, Wen A, Stone D, Kingsbury PR, Rasmussen LV, Adekkanattu P, Luo Y, Pathak J, Liu H, Jiang G. Applying a FHIR-based Data Normalization Pipeline to the Identification of Patients with Obesity and Its Comorbidities from Discharge Summaries. AMIA Informatics Summit CRI 2019. [podium abstract](https://informaticssummit2019.zerista.com/event/member/542977).
