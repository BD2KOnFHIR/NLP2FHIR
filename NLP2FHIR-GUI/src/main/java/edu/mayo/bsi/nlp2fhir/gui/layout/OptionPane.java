package edu.mayo.bsi.nlp2fhir.gui.layout;

import edu.mayo.bsi.nlp2fhir.gui.GUI;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.PipelineTask;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.options.Option;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.tasks.DeserializationTask;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.tasks.ResourceTask;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.tasks.SerializationTask;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class OptionPane extends JPanel {

    private GUI gui;
    // - Deserialization
    private DeserializationTask cr;
    // - Resource Production
    private ResourceTask ae;
    // - Serialization
    private SerializationTask cc;

    public OptionPane(DeserializationTask cr, ResourceTask ae, SerializationTask cc, GUI gui) {
        super(new RelativeLayout(RelativeLayout.Y_AXIS));
        this.gui = gui;
        this.cr = cr;
        this.ae = ae;
        this.cc = cc;
        add(createPropertyPane(cr));
        add(createPropertyPane(ae));
        add(createPropertyPane(cc));
    }

    private JPanel createPropertyPane(PipelineTask task) {
        JPanel panel = new JPanel(new RelativeLayout(RelativeLayout.Y_AXIS));
        String tracked = task.trackUpdateOption();
        for (Map.Entry<String, List<Option>> e : task.getActiveOptions().entrySet()) {
            JPanel taskPane = new JPanel(new RelativeLayout(RelativeLayout.Y_AXIS));
            if (e.getValue().size() > 1) { // Option group
                Option first = e.getValue().get(0);
                JLabel label = new JLabel(first.getName() + ":  ");
                @SuppressWarnings("unchecked") JComboBox<Object> flag = (JComboBox<Object>) produceOptionSelection(first, tracked != null && tracked.equals(first.getName()));
                taskPane.add(label);
                taskPane.add(flag);
                List<Component> ctrld = new LinkedList<>();
                for (int i = 1; i < e.getValue().size(); i++) {
                    Option option = e.getValue().get(i); // TODO: nicer way of doing this?
                    JLabel optionLabel = new JLabel(option.getName() + ":  ");
                    Component optionValues = produceOptionSelection(option, tracked != null && tracked.equals(option.getName()));
                    taskPane.add(optionLabel);
                    taskPane.add(optionValues);
                    ctrld.add(optionLabel);
                    ctrld.add(optionValues);
                }
                flag.addItemListener(event -> {
                    if (event.getStateChange() == ItemEvent.SELECTED) {
                        for (Component c : ctrld) {
                            c.setVisible(flag.getSelectedIndex() == 0);
                        }
                    }
                });
            } else {
                Option option = e.getValue().get(0);
                JLabel optionLabel = new JLabel(option.getName() + ":  ");
                Component optionValues = produceOptionSelection(option, tracked != null && tracked.equals(option.getName()));
                taskPane.add(optionLabel);
                taskPane.add(optionValues);
            }
            panel.add(taskPane);
        }
        return panel;
    }

    private void resetAndRedraw() {
        this.removeAll();
        gui.getOptionsBarrier().clear();
        gui.getRegisteredEvents().clear();
        add(createPropertyPane(cr));
        add(createPropertyPane(ae));
        add(createPropertyPane(cc));
        this.setVisible(false);
        this.setVisible(true);
    }
    private Component produceOptionSelection(final Option option, boolean tracked) {

        if (option.getMaxMultiselect() == 0) {
            // Text pane
            JTextField textPane = new JTextField("", 40);
            // Synchronize with backing option
            textPane.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    option.setValue(textPane.getText());
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    option.setValue(textPane.getText());
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    option.setValue(textPane.getText());
                }
            });
            return textPane;
        } else if (option.getMaxMultiselect() == 1) {
            JComboBox<Object> options = new JComboBox<>(option.getOptions());
            options.setSelectedIndex(option.getSelectedIndices().length > 0 ? option.getSelectedIndices()[0] : 0);
            options.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    option.setSelectedIndices(new int[]{options.getSelectedIndex()});
                    if (tracked) {
                        resetAndRedraw();
                    }
                }
            });
            return options;
        } else if (option.getMaxMultiselect() <= -1) { // Unlimited multiselect
            JList<Object> options = new JList<>(option.getOptions());
            // No need to add a barrier here as this gets updated immediately whenever selection changes
            options.setSelectedIndices(option.getSelectedIndices());
            options.addListSelectionListener(e -> {
                option.setSelectedIndices(options.getSelectedIndices());
                if (tracked) {
                    resetAndRedraw();
                }
            });
            return new JScrollPane(options);
        } else { // Multi-select with limits
            throw new UnsupportedOperationException("Not needed yet");
        }
    }
}
