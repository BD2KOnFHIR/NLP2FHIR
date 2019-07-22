package edu.mayo.bsi.nlp2fhir.gui;

import edu.mayo.bsi.nlp2fhir.gui.layout.OptionPane;
import edu.mayo.bsi.nlp2fhir.gui.layout.RelativeLayout;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.BuildablePipeline;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.tasks.DeserializationTask;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.tasks.ResourceTask;
import edu.mayo.bsi.nlp2fhir.gui.model.pipelinebuilder.tasks.SerializationTask;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.ohnlp.medtime.ae.MedTimeAnnotatorRuntimeInterceptor;

public class GUI {

    // === Pipeline Components ===
    // Collection Reader Equivalent
    private final DeserializationTask cr;
    // Analysis Engine Equivalent
    private final ResourceTask ae;
    // Cas Consumer Equivalent
    private final SerializationTask cc;

    // === Miscellaneous ===
    // A list of boolean flags representing completed saves of an object's state
    // Execution should wait for all flags to be true, and reset to false
    private final List<AtomicBoolean> optionsBarrier;
    private final List<ActionListener> registeredEvents;

    public GUI() {
        // - Init Pipeline
        cr = new DeserializationTask();
        ae = new ResourceTask();
        cc = new SerializationTask();
        // - Init Swing Element Dependencies
        optionsBarrier = new LinkedList<>();
        registeredEvents = new LinkedList<>();
        // - Init Swing Elements
        JFrame window = new JFrame("FHIR Resource Creation Tool");
        window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        RelativeLayout rl = new RelativeLayout(RelativeLayout.Y_AXIS);
        rl.setFill(true);
        JPanel contentPane = new JPanel(rl);
        contentPane.add(createUMLSValidation());
        OptionPane options = new OptionPane(cr, ae, cc, this);
        contentPane.add(new JScrollPane(options));
        contentPane.add(createExecuteButton());
        window.add(contentPane);
        // - Display GUI Window
        window.pack();
        window.setVisible(true);
    }

    private Component createUMLSValidation() {
        JPanel ret = new JPanel(new RelativeLayout(RelativeLayout.X_AXIS));
        JPanel user = new JPanel();
        user.add(new JLabel("UMLS Username: "));
        JTextField userField = new JTextField(10);
        userField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                System.setProperty("ctakes.umlsuser", userField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                System.setProperty("ctakes.umlsuser", userField.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                System.setProperty("ctakes.umlsuser", userField.getText());
            }
        });
        user.add(userField);
        JPanel pass = new JPanel();
        pass.add(new JLabel("UMLS Password: "));
        JTextField passField = new JPasswordField(10);
        passField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                System.setProperty("ctakes.umlspw", passField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                System.setProperty("ctakes.umlspw", passField.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                System.setProperty("ctakes.umlspw", passField.getText());
            }
        });
        pass.add(passField);
        ret.add(user);
        ret.add(pass);
        return ret;
    }

    private Component createExecuteButton() {
        JButton execute = new JButton("Run Pipeline");
        execute.setActionCommand("run");
        execute.addActionListener(e -> {
            // Disable multi-click
            execute.setText("Executing Pipeline");
            execute.setEnabled(false);
            // Run remainder of logic in separate thread
            Future<?> future = Executors.newSingleThreadExecutor().submit(() -> {
                // Forward the action command to all registered listeners in parallel
                ExecutorService pool = Executors.newCachedThreadPool();
                for (ActionListener listener : registeredEvents) {
                    pool.submit(() ->  listener.actionPerformed(e));
                }
                pool.shutdown();
                // Wait for all options to finish updating in-sync
                for (final AtomicBoolean lock : optionsBarrier) {
                    //noinspection SynchronizationOnLocalVariableOrMethodParameter
                    synchronized (lock) {
                        while (!lock.get()) {
                            try {
                                lock.wait(1000);
                            } catch (InterruptedException ignored) {
                            }
                        }
                        // Reset the lock back to false in preparation for next run
                        lock.set(false);
                    }
                }
                // All options up to date, run pipeline
                CompletableFuture<Boolean> result = buildAndExecutePipeline();
                Boolean ret = null;
                try {
                    while (ret == null) {
                        try {
                            ret = result.get();
                        } catch (InterruptedException ignored) {
                        }
                    }

                } catch (ExecutionException e1) {
                    e1.printStackTrace();
                    throw new RuntimeException(e1);
                    // TODO handle this
                }
                // Processing complete, re-enable pipeline
                execute.setText("Run Pipeline");
                execute.setEnabled(true);
            });
            Executors.newSingleThreadExecutor().submit(() -> {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e1) {
                    e1.printStackTrace();
                    execute.setText("Run Pipeline");
                    execute.setEnabled(true);
                }
            });

        });
        return execute;
    }

    public CompletableFuture<Boolean> buildAndExecutePipeline() {
        BuildablePipeline pipeline = new BuildablePipeline();
        cr.construct(pipeline);
        ae.construct(pipeline);
        cc.construct(pipeline);
        return pipeline.executePipeline();
    }

    public List<AtomicBoolean> getOptionsBarrier() {
        return optionsBarrier;
    }

    public static void main(String... args) {
        System.setProperty("vocab.src.dir", System.getProperty("user.dir"));
        ByteBuddyAgent.install();
        new ByteBuddy()
                .redefine(MedTimeAnnotator.class)
                .method(ElementMatchers.named("process"))
                .intercept(MethodDelegation.to(new MedTimeAnnotatorRuntimeInterceptor()))
                .field(ElementMatchers.named("newYearValue"))
                .value("0")
                .field(ElementMatchers.named("cYearValue"))
                .value("0")
                .make()
                .load(GUI.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
        new GUI();
    }

    public List<ActionListener> getRegisteredEvents() {
        return registeredEvents;
    }

    public void registerListener(ActionListener listener) {
        registeredEvents.add(listener);
    }
}
