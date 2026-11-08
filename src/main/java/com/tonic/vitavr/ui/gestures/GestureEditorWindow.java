package com.tonic.vitavr.ui.gestures;

import com.tonic.vitavr.gestures.actions.Game;
import com.tonic.vitavr.gestures.actions.GestureRunner;
import com.tonic.vitavr.gestures.model.Binding;
import com.tonic.vitavr.gestures.model.Gesture;
import com.tonic.vitavr.gestures.model.GestureHand;
import com.tonic.vitavr.gestures.model.GestureKind;
import com.tonic.vitavr.gestures.model.Profile;
import com.tonic.vitavr.gestures.model.steps.AbstractStep;
import com.tonic.vitavr.gestures.store.GestureJson;
import com.tonic.vitavr.gestures.store.GestureStore;
import com.tonic.vitavr.gestures.store.Gestures;
import com.tonic.vitavr.gestures.vr.GestureOutcome;
import com.tonic.vitavr.ui.Style;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The desktop gesture editor: the profiles, the gesture library with each gesture's settings and looping preview, and
 * what the selected gesture does in the profile in use, step by step; every change is saved at once.
 */
public final class GestureEditorWindow
{
    private final EditorContext context;
    private final JFrame frame = new JFrame("VitaVR gestures");
    private final JComboBox<String> profiles = new JComboBox<>();
    private final DefaultListModel<Gesture> gestureModel = new DefaultListModel<>();
    private final JList<Gesture> gestureList = EditorKit.list();
    private final JPanel gestureDetail = new JPanel(new BorderLayout());
    private final JPanel previewHolder = new JPanel(new BorderLayout());
    private final DefaultListModel<String> stepModel = new DefaultListModel<>();
    private final JList<String> stepList = EditorKit.list();
    private final JPanel stepForm = new EditorKit.FitsWidth(new BorderLayout());
    private final JPanel bindingFooter = new JPanel(new BorderLayout());
    private final JLabel status = EditorKit.muted(" ");
    private final Runnable storeChanged = () -> SwingUtilities.invokeLater(this::followStore);
    private Consumer<Gesture> previewShows = gesture -> { };
    private int shownVersion = -1;
    private boolean loading;
    private String selectedId;
    private int selectedStep = -1;

    /**
     * Makes the editor window, hidden. Call on the Swing thread.
     *
     * @param store        The gestures.
     * @param runner       Runs a binding for Run now.
     * @param game         The game, read for taking items and prayers from it.
     * @param client       The client.
     * @param clientThread The client thread.
     * @param itemManager  Item icons.
     * @return The window.
     */
    public static GestureEditorWindow create(GestureStore store, GestureRunner runner, Game game, Client client, ClientThread clientThread, ItemManager itemManager)
    {
        GestureEditorWindow window = new GestureEditorWindow(new EditorContext(store, runner, game, client, clientThread, itemManager));
        GesturePreview preview = new GesturePreview();
        window.preview(preview::show, preview);
        return window;
    }

    private GestureEditorWindow(EditorContext context)
    {
        this.context = context;
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(Style.px(8), Style.px(8)));
        content.setBackground(Style.BG_PRIMARY);
        content.setBorder(BorderFactory.createEmptyBorder(Style.px(10), Style.px(10), Style.px(10), Style.px(10)));
        frame.setContentPane(content);
        content.add(toolbar(), BorderLayout.NORTH);
        content.add(library(), BorderLayout.WEST);
        JSplitPane centre = new JSplitPane(JSplitPane.VERTICAL_SPLIT, previewHolder, binding());
        centre.setBorder(null);
        centre.setResizeWeight(0.4);
        centre.setBackground(Style.BG_PRIMARY);
        previewHolder.setBackground(Style.BG_SECONDARY);
        previewHolder.setPreferredSize(new Dimension(Style.px(560), Style.px(300)));
        content.add(centre, BorderLayout.CENTER);
        content.add(status, BorderLayout.SOUTH);
        frame.setPreferredSize(new Dimension(Style.px(1150), Style.px(780)));
        frame.pack();
        frame.addWindowListener(new java.awt.event.WindowAdapter()
        {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e)
            {
                context.getStore().removeListener(storeChanged);
            }
        });
    }

    /**
     * Closes the window for good and stops following the gestures. Call on the Swing thread.
     */
    public void close()
    {
        context.getStore().removeListener(storeChanged);
        frame.dispose();
    }

    /**
     * Shows the window, in front, reading the gestures afresh. Call on the Swing thread.
     */
    public void open()
    {
        context.getStore().removeListener(storeChanged);
        context.getStore().addListener(storeChanged);
        shownVersion = -1;
        followStore();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.toFront();
    }

    /**
     * @param shows Told the gesture selected, or null, to replay it; it is put above the steps.
     * @param view  The preview.
     */
    void preview(Consumer<Gesture> shows, Component view)
    {
        previewShows = shows;
        previewHolder.removeAll();
        previewHolder.add(view, BorderLayout.CENTER);
        previewHolder.revalidate();
    }

    private JComponent toolbar()
    {
        profiles.setFont(Style.font(Style.FONT_SIZE));
        profiles.addActionListener(e ->
        {
            Object chosen = profiles.getSelectedItem();
            if (!loading && chosen != null && !chosen.equals(snapshot().getActive()))
            {
                update(gestures -> gestures.setActive((String) chosen));
            }
        });
        JPanel bar = EditorKit.row(EditorKit.label("Profile"), profiles,
                EditorKit.button("New", this::newProfile),
                EditorKit.button("Rename", this::renameProfile),
                EditorKit.button("Duplicate", this::duplicateProfile),
                EditorKit.button("Delete", this::deleteProfile),
                EditorKit.button("Undo", () ->
                {
                    if (!context.getStore().undo())
                    {
                        status.setText("Nothing to undo");
                    }
                }));
        return bar;
    }

    private JComponent library()
    {
        JPanel side = new JPanel(new BorderLayout(0, Style.px(6)));
        side.setOpaque(false);
        side.setPreferredSize(new Dimension(Style.px(300), 0));
        gestureList.setModel(gestureModel);
        gestureList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        gestureList.setCellRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused)
            {
                Gesture gesture = (Gesture) value;
                Binding binding = snapshot().binding(gesture.getId());
                String text = "<html><b>" + escape(gesture.getName()) + "</b> <font color='#9AA0A6'>" + gesture.getKind() + ", " + gesture.getHand()
                        + (gesture.isEnabled() ? "" : ", off") + "</font><br><font color='#9AA0A6'>" + escape(binding == null ? "Not bound" : binding.describe())
                        + "</font></html>";
                JLabel cell = (JLabel) super.getListCellRendererComponent(list, text, index, selected, focused);
                cell.setBorder(BorderFactory.createEmptyBorder(Style.px(4), Style.px(6), Style.px(4), Style.px(6)));
                cell.setBackground(selected ? Style.SELECTION : Style.BG_SECONDARY);
                cell.setForeground(Style.TEXT);
                return cell;
            }
        });
        gestureList.addListSelectionListener(e ->
        {
            if (!loading && !e.getValueIsAdjusting())
            {
                Gesture chosen = gestureList.getSelectedValue();
                selectedId = chosen == null ? null : chosen.getId();
                selectedStep = -1;
                showGesture();
            }
        });
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(EditorKit.label("Gestures"), BorderLayout.NORTH);
        top.add(EditorKit.muted("<html><div style='width:" + Style.px(210) + "px'>Record new gestures in the headset, in the Gestures tab of the VR settings</div></html>"), BorderLayout.SOUTH);
        side.add(top, BorderLayout.NORTH);
        side.add(EditorKit.scroll(gestureList), BorderLayout.CENTER);
        gestureDetail.setOpaque(false);
        side.add(gestureDetail, BorderLayout.SOUTH);
        return side;
    }

    private JComponent binding()
    {
        JPanel panel = new JPanel(new BorderLayout(Style.px(8), Style.px(8)));
        panel.setOpaque(false);
        stepList.setModel(stepModel);
        stepList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stepList.addListSelectionListener(e ->
        {
            if (!loading && !e.getValueIsAdjusting())
            {
                selectedStep = stepList.getSelectedIndex();
                showStepForm();
            }
        });
        JPanel steps = new JPanel(new BorderLayout(0, Style.px(6)));
        steps.setOpaque(false);
        steps.setPreferredSize(new Dimension(Style.px(330), 0));
        steps.add(EditorKit.label("What it does, in order"), BorderLayout.NORTH);
        steps.add(EditorKit.scroll(stepList), BorderLayout.CENTER);
        JPanel stepButtons = EditorKit.row();
        JPopupMenu kinds = Style.menu();
        for (Map.Entry<String, Supplier<AbstractStep>> kind : StepForms.KINDS.entrySet())
        {
            Style.menuItem(kinds, kind.getKey(), () -> addStep(kind.getValue().get()));
        }
        javax.swing.JButton add = EditorKit.button("Add step…", () -> { });
        add.addActionListener(e -> kinds.show(add, 0, add.getHeight()));
        stepButtons.add(add);
        stepButtons.add(EditorKit.button("Remove", () -> moveStep(0)));
        stepButtons.add(EditorKit.button("Up", () -> moveStep(-1)));
        stepButtons.add(EditorKit.button("Down", () -> moveStep(1)));
        stepButtons.add(EditorKit.button("Copy", () -> moveStep(2)));
        steps.add(stepButtons, BorderLayout.SOUTH);
        panel.add(steps, BorderLayout.WEST);
        stepForm.setOpaque(false);
        JScrollPane formScroll = new JScrollPane(stepForm);
        formScroll.setBorder(BorderFactory.createLineBorder(Style.BORDER));
        formScroll.getViewport().setBackground(Style.BG_PRIMARY);
        formScroll.getVerticalScrollBar().setUnitIncrement(Style.px(16));
        panel.add(formScroll, BorderLayout.CENTER);
        bindingFooter.setOpaque(false);
        panel.add(bindingFooter, BorderLayout.SOUTH);
        return panel;
    }

    private Gestures snapshot()
    {
        return context.getStore().snapshot();
    }

    private void update(Consumer<Gestures> change)
    {
        context.getStore().update(change);
        shownVersion = context.getStore().version();
    }

    /**
     * Shows the store afresh after a change made elsewhere, such as in the headset or an undo, keeping the selection.
     */
    private void followStore()
    {
        if (context.getStore().version() == shownVersion)
        {
            refreshGestureItems();
            return;
        }
        shownVersion = context.getStore().version();
        Gestures gestures = snapshot();
        loading = true;
        try
        {
            profiles.removeAllItems();
            for (Profile profile : gestures.getProfiles())
            {
                profiles.addItem(profile.getName());
            }
            profiles.setSelectedItem(gestures.activeProfile() == null ? null : gestures.activeProfile().getName());
            gestureModel.clear();
            int select = -1;
            for (Gesture gesture : gestures.getLibrary())
            {
                if (gesture.getId().equals(selectedId))
                {
                    select = gestureModel.size();
                }
                gestureModel.addElement(gesture);
            }
            if (select < 0 && !gestureModel.isEmpty())
            {
                select = 0;
                selectedId = gestureModel.get(0).getId();
                selectedStep = -1;
            }
            if (select < 0)
            {
                selectedId = null;
            }
            gestureList.setSelectedIndex(select);
        }
        finally
        {
            loading = false;
        }
        String problem = context.getStore().getProblem();
        status.setText(problem.isEmpty() ? " " : problem);
        showGesture();
    }

    private Gesture selected()
    {
        return selectedId == null ? null : snapshot().gesture(selectedId);
    }

    private void showGesture()
    {
        Gesture gesture = selected();
        previewShows.accept(gesture);
        gestureDetail.removeAll();
        if (gesture != null)
        {
            gestureDetail.add(detail(gesture), BorderLayout.CENTER);
        }
        gestureDetail.revalidate();
        gestureDetail.repaint();
        showSteps();
    }

    private JComponent detail(Gesture gesture)
    {
        EditorKit.Form form = new EditorKit.Form();
        EditorKit.titled(form, "Gesture");
        String id = gesture.getId();
        form.field("Name", EditorKit.field(gesture.getName(), text -> editGesture(id, changing -> changing.setName(text.trim().isEmpty() ? changing.getName() : text.trim()))));
        form.wide(EditorKit.check("On", gesture.isEnabled(), on -> editGesture(id, changing -> changing.setEnabled(on))));
        if (gesture.getKind() != GestureKind.TWO_HAND)
        {
            GestureHand[] hands = {GestureHand.LEFT, GestureHand.RIGHT, GestureHand.EITHER};
            form.field("Hand", EditorKit.combo(hands, gesture.getHand(), hand -> editGesture(id, changing -> changing.setHand(hand))));
        }
        JSlider sensitivity = new JSlider(0, 100, gesture.getSensitivity());
        sensitivity.setOpaque(false);
        sensitivity.setToolTipText("Higher counts gestures made less exactly; lower needs them made more like the takes");
        sensitivity.addChangeListener(e ->
        {
            if (!sensitivity.getValueIsAdjusting())
            {
                editGesture(id, changing -> changing.setSensitivity(sensitivity.getValue()));
            }
        });
        form.field("Sensitivity", sensitivity);
        if (gesture.getKind() == GestureKind.SPOT)
        {
            form.field("Spot size (cm)", EditorKit.spinner((int) Math.round(gesture.getRadius() * 100), 3, 40, 1, centimetres -> editGesture(id, changing -> changing.setRadius(centimetres / 100.0))));
        }
        if (gesture.getKind() == GestureKind.FLICK)
        {
            form.wide(EditorKit.check("Needs the arm input held", gesture.isArmed(), on -> editGesture(id, changing -> changing.setArmed(on))));
        }
        form.wide(EditorKit.row(EditorKit.button("Delete gesture", () ->
        {
            if (JOptionPane.showConfirmDialog(frame, "Delete " + gesture.getName() + " and what it does in every profile?", "Delete gesture",
                    JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
            {
                update(gestures -> gestures.getLibrary().removeIf(each -> each.getId().equals(id)));
                shownVersion = -1;
                followStore();
            }
        })));
        return form;
    }

    private void editGesture(String id, Consumer<Gesture> change)
    {
        update(gestures ->
        {
            Gesture gesture = gestures.gesture(id);
            if (gesture != null)
            {
                change.accept(gesture);
            }
        });
        refreshGestureItems();
        previewShows.accept(selected());
    }

    /**
     * Puts the store's current copy of each listed gesture in the list, so a name, hand or binding changed here shows.
     */
    private void refreshGestureItems()
    {
        Gestures gestures = snapshot();
        boolean wasLoading = loading;
        loading = true;
        try
        {
            for (int i = 0; i < gestureModel.size(); i++)
            {
                Gesture now = gestures.gesture(gestureModel.get(i).getId());
                if (now != null)
                {
                    gestureModel.set(i, now);
                }
            }
        }
        finally
        {
            loading = wasLoading;
        }
        gestureList.repaint();
    }

    private Binding binding(Gestures gestures)
    {
        return selectedId == null ? null : gestures.binding(selectedId);
    }

    private void showSteps()
    {
        Binding binding = binding(snapshot());
        loading = true;
        try
        {
            stepModel.clear();
            if (binding != null)
            {
                for (int i = 0; i < binding.getSteps().size(); i++)
                {
                    stepModel.addElement((i + 1) + ". " + binding.getSteps().get(i).describe());
                }
            }
            if (selectedStep >= stepModel.size())
            {
                selectedStep = stepModel.size() - 1;
            }
            if (selectedStep < 0 && !stepModel.isEmpty())
            {
                selectedStep = 0;
            }
            stepList.setSelectedIndex(selectedStep);
        }
        finally
        {
            loading = false;
        }
        showStepForm();
        showFooter(binding);
    }

    private void showStepForm()
    {
        stepForm.removeAll();
        Binding binding = binding(snapshot());
        if (selectedId == null)
        {
            stepForm.add(EditorKit.muted("  Choose a gesture"), BorderLayout.NORTH);
        }
        else if (binding == null || binding.getSteps().isEmpty())
        {
            stepForm.add(EditorKit.muted("  Not bound in this profile: add a step"), BorderLayout.NORTH);
        }
        else if (selectedStep >= 0 && selectedStep < binding.getSteps().size())
        {
            int index = selectedStep;
            List<String> names = new ArrayList<>();
            for (Profile profile : snapshot().getProfiles())
            {
                names.add(profile.getName());
            }
            stepForm.add(StepForms.form(context, binding.getSteps().get(index), names, changed ->
            {
                editBinding(edited -> edited.getSteps().set(index, changed));
                stepModel.set(index, (index + 1) + ". " + changed.describe());
                refreshGestureItems();
            }), BorderLayout.CENTER);
        }
        stepForm.revalidate();
        stepForm.repaint();
    }

    private void showFooter(Binding binding)
    {
        bindingFooter.removeAll();
        if (selectedId != null)
        {
            Binding shown = binding == null ? new Binding() : binding;
            bindingFooter.add(new ConditionsPanel(context, shown.getConditions(), "The gesture only runs when", conditions -> editBinding(edited -> edited.setConditions(conditions))),
                    BorderLayout.CENTER);
            bindingFooter.add(EditorKit.row(EditorKit.label("Cooldown (ms)"), EditorKit.spinner(shown.getCooldownMs(), 0, 10_000, 100,
                    cooldown -> editBinding(edited -> edited.setCooldownMs(cooldown))), EditorKit.button("Run now", this::runNow)), BorderLayout.SOUTH);
        }
        bindingFooter.revalidate();
        bindingFooter.repaint();
    }

    private void editBinding(Consumer<Binding> change)
    {
        String id = selectedId;
        if (id == null)
        {
            return;
        }
        update(gestures ->
        {
            Profile profile = gestures.activeProfile();
            if (profile != null)
            {
                change.accept(profile.getBindings().computeIfAbsent(id, key -> new Binding()));
            }
        });
    }

    private void addStep(AbstractStep step)
    {
        if (selectedId == null)
        {
            status.setText("Choose a gesture first");
            return;
        }
        editBinding(edited -> edited.getSteps().add(step));
        Binding binding = binding(snapshot());
        selectedStep = binding == null ? -1 : binding.getSteps().size() - 1;
        showSteps();
        refreshGestureItems();
    }

    /**
     * @param how -1 up, 1 down, 0 remove, 2 copy.
     */
    private void moveStep(int how)
    {
        int index = selectedStep;
        Binding binding = binding(snapshot());
        if (binding == null || index < 0 || index >= binding.getSteps().size())
        {
            return;
        }
        int target = index + how;
        if ((how == -1 || how == 1) && (target < 0 || target >= binding.getSteps().size()))
        {
            return;
        }
        editBinding(edited ->
        {
            List<AbstractStep> steps = edited.getSteps();
            if (how == 0)
            {
                steps.remove(index);
            }
            else if (how == 2)
            {
                steps.add(index + 1, GestureJson.copy(steps.get(index), AbstractStep.class));
            }
            else
            {
                steps.add(target, steps.remove(index));
            }
        });
        selectedStep = how == 0 ? Math.min(index, binding.getSteps().size() - 2) : how == 2 ? index + 1 : target;
        showSteps();
        refreshGestureItems();
    }

    private void runNow()
    {
        Gesture gesture = selected();
        if (gesture == null)
        {
            return;
        }
        Binding binding = binding(snapshot());
        status.setText("Running " + gesture.getName() + "…");
        context.getClientThread().invoke(() ->
        {
            GestureOutcome outcome = context.getRunner().run(gesture, binding);
            SwingUtilities.invokeLater(() -> status.setText((outcome.isDone() ? "Ran: " : "Could not: ") + outcome.getText()));
        });
    }

    private void newProfile()
    {
        String name = askName("New profile", "");
        if (name != null)
        {
            update(gestures ->
            {
                gestures.getProfiles().add(Profile.named(name));
                gestures.setActive(name);
            });
            shownVersion = -1;
            followStore();
        }
    }

    private void renameProfile()
    {
        Profile active = snapshot().activeProfile();
        if (active == null)
        {
            return;
        }
        String old = active.getName();
        String name = askName("Rename profile", old);
        if (name != null)
        {
            update(gestures ->
            {
                gestures.profile(old).setName(name);
                gestures.setActive(name);
            });
            shownVersion = -1;
            followStore();
        }
    }

    private void duplicateProfile()
    {
        Profile active = snapshot().activeProfile();
        if (active == null)
        {
            return;
        }
        String name = askName("Duplicate profile", active.getName() + " copy");
        if (name != null)
        {
            update(gestures ->
            {
                Profile copy = GestureJson.copy(gestures.profile(active.getName()), Profile.class);
                copy.setName(name);
                gestures.getProfiles().add(copy);
                gestures.setActive(name);
            });
            shownVersion = -1;
            followStore();
        }
    }

    private void deleteProfile()
    {
        Gestures gestures = snapshot();
        Profile active = gestures.activeProfile();
        if (active == null || gestures.getProfiles().size() < 2)
        {
            status.setText("There must be at least one profile");
            return;
        }
        if (JOptionPane.showConfirmDialog(frame, "Delete profile " + active.getName() + "?", "Delete profile", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
        {
            return;
        }
        update(changing ->
        {
            changing.getProfiles().removeIf(profile -> profile.getName().equals(active.getName()));
            changing.setActive(changing.getProfiles().get(0).getName());
        });
        shownVersion = -1;
        followStore();
    }

    private String askName(String title, String start)
    {
        Object typed = JOptionPane.showInputDialog(frame, "Name", title, JOptionPane.PLAIN_MESSAGE, null, null, start);
        if (typed == null)
        {
            return null;
        }
        String name = typed.toString().trim();
        if (name.isEmpty())
        {
            return null;
        }
        Profile taken = snapshot().profile(name);
        if (taken != null && !name.equals(start))
        {
            status.setText("A profile is already called " + name);
            return null;
        }
        return name;
    }

    private static String escape(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
