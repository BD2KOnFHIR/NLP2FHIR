<!DOCTYPE html>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<html>
<head>
    <title>NLP2FHIR Web Demonstration</title>
    <script src="webjars/jquery/3.2.1/jquery.min.js"></script>

    <link rel="stylesheet" href="webjars/bootstrap/3.3.7/css/bootstrap.min.css">
    <link rel="stylesheet" href="webjars/bootstrap/3.3.7/css/bootstrap-theme.min.css">
    <script src="webjars/bootstrap/3.3.7/js/bootstrap.min.js"></script>
    <script src="webjars/angularjs/1.6.6/angular.min.js"></script>
    <script src="js/nlp2fhir.js"></script>
</head>
<body ng-app="NLP2FHIRAPP" ng-controller="NLP2FHIRCTRL as NLP2FHIR">
<ul style="padding-left: 20px;">
    <li style="list-style: none" ng-repeat="section in NLP2FHIR.request.sections track by $index"
        ng-init="secIdx = $index">
        <div class="row">
            <div class="col-xs-10 pull-left">
                <label>Section ID:
                    <select class="input-small" ng-model="NLP2FHIR.request.sections[secIdx].id">
                        <option ng-repeat="(id, val) in NLP2FHIR.SECTIONS" value="{{id}}">{{id}} - {{val}}</option>
                    </select>
                </label>
            </div>
            <div class="col-xs-2 pull-right">
                <button type="button" class="btn btn-btn-default"
                        ng-click="NLP2FHIR.request.removeSection(secIdx)">
                    Remove section
                </button>
            </div>
        </div>
        <div class="row">
            <div class="col-xs-12">
                <label>Document Content:
                    <textarea class="form-control" cols="20" rows="20" name="documentContent" title="Document Content"
                              ng-model="NLP2FHIR.request.sections[secIdx].body">
                    </textarea>
                </label>
            </div>
        </div>
    </li>
    <li>
        <div class="row">
            <div class="col-xs-12">
                <button type="button" class="btn btn-btn-default"
                        ng-click="NLP2FHIR.request.addSection();">
                    Add new section
                </button>
            </div>
        </div>
    </li>
</ul>
<div class="clearfix"></div>
<div class="row">
    <div class="col-xs-12">
        <button type="button" class="btn btn-btn-default"
                ng-click="NLP2FHIR.generate();">
            Generate FHIR Resources
        </button>
    </div>
</div>

</body>
</html>
