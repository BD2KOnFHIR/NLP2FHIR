package edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.options;

import java.util.LinkedList;
import java.util.List;

public class Option {
    private boolean required;
    private Object[] options;
    private int[] selected;
    private int maxMultiselect;
    private String name;
    private String value;

    public Option(String name, boolean required, int maxMultiselect, Object... options) {
        this.name = name;
        this.required = required;
        this.maxMultiselect = maxMultiselect;
        this.options = options;
        this.selected = new int[0];
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Object[] getOptions() {
        return options;
    }

    public void setOptions(Object[] options) {
        this.options = options;
    }

    public int[] getSelectedIndices() {
        return selected;
    }

    public void setSelectedIndices(int[] idx) {
        this.selected = idx;
    }

    public int getMaxMultiselect() {
        return maxMultiselect;
    }

    public List<Object> getSelected() {
        // TODO no validation
        List<Object> ret = new LinkedList<>();
        for (int i : selected) {
            ret.add(options[i]);
        }
        if (ret.size() == 0) {
            ret.add(options[0]);
        }
        return ret;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
