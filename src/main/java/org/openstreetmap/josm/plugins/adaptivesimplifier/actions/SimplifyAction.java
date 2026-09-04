/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2026 JOSM Plugin Builder
 */
package org.openstreetmap.josm.plugins.adaptivesimplifier.actions;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.*;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.gui.*;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Shortcut;
import javax.swing.*;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.PriorityQueue;
import java.util.Arrays;
import java.util.stream.Collectors;
import static org.openstreetmap.josm.tools.I18n.tr;

public class SimplifyAction extends JosmAction {
    public SimplifyAction() {
        super(tr("Adaptive Simplifier"), "adaptive-simplifier", tr("Adaptive Simplifier"),
              Shortcut.registerShortcut("mytools:adaptive_simplify", tr("Mytools: {0}", tr("Adaptive Simplifier")), 
              KeyEvent.VK_Y, Shortcut.CTRL_SHIFT), true);
        putValue(Action.ACCELERATOR_KEY, getShortcut().getKeyStroke());
    }
    @Override
    public void actionPerformed(java.awt.event.ActionEvent e) {
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        if (ds == null) return;
        List<Way> ways = ds.getSelectedWays().stream().collect(Collectors.toList());
        if (ways.isEmpty()) {
            JOptionPane.showMessageDialog(MainApplication.getMainFrame(), tr("No ways selected to simplify."));
            return;
        }
        double lastThr = Config.getPref().getDouble("adaptivesimplifier.threshold", 0.2);
        boolean lastMergeEnabled = Config.getPref().getBoolean("adaptivesimplifier.merge.enabled", false);
        double lastMergeThr = Config.getPref().getDouble("adaptivesimplifier.merge.threshold", 0.1);
        boolean lastPriorityEnabled = Config.getPref().getBoolean("adaptivesimplifier.priority.enabled", false);
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(lastThr, 0.01, 100.0, 0.05));
        setupAutoClamp(spinner, 0.01, 100.0, false);
        JCheckBox mergeCheckbox = new JCheckBox(tr("Merge nearby nodes"), lastMergeEnabled);
        JSpinner mergeSpinner = new JSpinner(new SpinnerNumberModel(lastMergeThr, 0.01, 100.0, 0.05));
        setupAutoClamp(mergeSpinner, 0.01, 100.0, false);
        JCheckBox priorityCheckbox = new JCheckBox(tr("Priority to preserve author nodes"), lastPriorityEnabled);
        mergeSpinner.setEnabled(lastMergeEnabled);
        mergeCheckbox.addActionListener(ev -> mergeSpinner.setEnabled(mergeCheckbox.isSelected()));
        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = java.awt.GridBagConstraints.WEST; gbc.insets = new java.awt.Insets(2, 2, 2, 5);
        panel.add(new javax.swing.JLabel(tr("Threshold (meters):")), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.insets = new java.awt.Insets(2, 2, 2, 2);
        panel.add(spinner, gbc);
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL; gbc.weightx = 0.0; gbc.insets = new java.awt.Insets(2, 2, 2, 2);
        panel.add(mergeCheckbox, gbc);
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL; gbc.weightx = 0.0; gbc.insets = new java.awt.Insets(2, 2, 2, 2);
        panel.add(priorityCheckbox, gbc);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1; gbc.fill = java.awt.GridBagConstraints.NONE; gbc.weightx = 0.0; gbc.insets = new java.awt.Insets(2, 2, 2, 5);
        panel.add(new javax.swing.JLabel(tr("Merge threshold (meters):")), gbc);
        gbc.gridx = 1; gbc.gridy = 3; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.insets = new java.awt.Insets(2, 2, 2, 2);
        panel.add(mergeSpinner, gbc);
        int res = JOptionPane.showConfirmDialog(MainApplication.getMainFrame(), panel, tr("Simplification settings"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            try {
                spinner.commitEdit();
                mergeSpinner.commitEdit();
            } catch (Exception ignored) {}
            ds.setSelected(ways);
            double threshold = (Double) spinner.getValue();
            boolean mergeEnabled = mergeCheckbox.isSelected();
            double mergeThreshold = (Double) mergeSpinner.getValue();
            boolean priorityEnabled = priorityCheckbox.isSelected();
            Config.getPref().putDouble("adaptivesimplifier.threshold", threshold);
            Config.getPref().putBoolean("adaptivesimplifier.merge.enabled", mergeEnabled);
            Config.getPref().putDouble("adaptivesimplifier.merge.threshold", mergeThreshold);
            Config.getPref().putBoolean("adaptivesimplifier.priority.enabled", priorityEnabled);
            runSimplification(ds, ways, threshold, mergeEnabled, mergeThreshold, priorityEnabled);
        }
    }
    private void runSimplification(DataSet ds, List<Way> ways, double threshold, boolean mergeEnabled, double mergeThreshold, boolean priorityEnabled) {
        JDialog pd = new JDialog(MainApplication.getMainFrame(), tr("Simplifying lines..."), false);
        JProgressBar bar = new JProgressBar(0, ways.size());
        bar.setPreferredSize(new java.awt.Dimension(300, 20));
        pd.add(bar);
        pd.pack();
        pd.setLocationRelativeTo(MainApplication.getMainFrame());

        new SwingWorker<BulkSimplifyCommand, Integer>() {
            @Override protected BulkSimplifyCommand doInBackground() {
                Map<Way, List<Node>> wayMap = new ConcurrentHashMap<>();
                Set<Node> allOriginalNodes = Collections.synchronizedSet(new HashSet<>());
                
                java.util.concurrent.atomic.AtomicInteger progress = new java.util.concurrent.atomic.AtomicInteger(0);
                Map<Node, org.openstreetmap.josm.data.coor.LatLon> allMovedNodes = new ConcurrentHashMap<>();
                ways.parallelStream().forEach(way -> {
                    List<Node> nodes = way.getNodes();
                    allOriginalNodes.addAll(nodes);
                    List<Node> result = nodes;
                    boolean changed;
                    do {
                        int sizeBefore = result.size();
                        result = simplifyPointByPoint(result, threshold);
                        if (mergeEnabled) {
                            result = mergeNearbyNodes(result, mergeThreshold, allMovedNodes);
                        }
                        changed = result.size() < sizeBefore;
                    } while (changed);
                    wayMap.put(way, result);
                    publish(progress.incrementAndGet());
                });

                Map<Node, Node> replacementMap = new HashMap<>();
                if (priorityEnabled) {
                Set<Node> keptNodesSet = wayMap.values().stream().flatMap(List::stream).collect(Collectors.toSet());
                Set<Way> selectedWaysSet = new HashSet<>(ways);
                List<Node> globalPool = new ArrayList<>(allOriginalNodes);
                globalPool.removeIf(n -> n.getId() <= 0 || keptNodesSet.contains(n) || n.isTagged() || !selectedWaysSet.containsAll(n.getParentWays()));

                ways.forEach(way -> {
                    Set<Node> wayOriginalNodes = new HashSet<>(way.getNodes());
                    wayMap.get(way).stream()
                        .filter(n -> n.getId() <= 0 && !replacementMap.containsKey(n))
                        .forEach(newNode -> {
                            Node best = null;
                            for (Node p : globalPool) {
                                if (wayOriginalNodes.contains(p)) { best = p; break; }
                            }
                            if (best != null) {
                                globalPool.remove(best);
                                replacementMap.put(newNode, best);
                            }
                        });
                });

                wayMap.values().stream().flatMap(List::stream)
                    .filter(n -> n.getId() <= 0 && !replacementMap.containsKey(n))
                    .distinct().forEach(newNode -> {
                        if (!globalPool.isEmpty()) {
                            Node best = globalPool.get(0);
                            double minDist = newNode.getEastNorth().distance(best.getEastNorth());
                            for (Node p : globalPool) {
                                double d = newNode.getEastNorth().distance(p.getEastNorth());
                                if (d < minDist) { minDist = d; best = p; }
                            }
                            globalPool.remove(best);
                            replacementMap.put(newNode, best);
                        }
                    });

                wayMap.replaceAll((way, nodes) -> nodes.stream()
                    .map(n -> replacementMap.getOrDefault(n, n))
                    .collect(Collectors.toList()));

                replacementMap.forEach((newNode, authorNode) -> allMovedNodes.put(authorNode, newNode.getCoor()));
                }

                Set<Node> nodesToKeep = new HashSet<>();
                for (List<Node> l : wayMap.values()) nodesToKeep.addAll(l);

                Set<Way> waysSet = new HashSet<>(ways);
                Set<Node> nodesToRemove = allOriginalNodes.stream()
                    .filter(n -> !nodesToKeep.contains(n))
                    .filter(n -> waysSet.containsAll(n.getParentWays()))
                    .collect(Collectors.toSet());

                return new BulkSimplifyCommand(ds, wayMap, nodesToRemove, allMovedNodes);
            }
            @Override protected void process(List<Integer> chunks) { bar.setValue(chunks.get(chunks.size()-1)); }
            @Override protected void done() {
                pd.dispose();
                try {
                    BulkSimplifyCommand cmd = get();
                    if (cmd != null && cmd.hasChanges()) {
                        UndoRedoHandler.getInstance().add(cmd);
                        JOptionPane.showMessageDialog(MainApplication.getMainFrame(), tr("Simplified {0} ways, removed {1} nodes.", cmd.getChangedCount(), cmd.getDeletedNodesCount()));
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
        pd.setVisible(true);
    }

    private static class BulkSimplifyCommand extends org.openstreetmap.josm.command.Command {
        private final Map<Way, List<Node>> newWayNodes;
        private final Map<Way, List<Node>> oldWayNodes;
        private final Map<Way, Boolean> wayOldModified;
        private final Set<Node> nodesToDelete;
        private final Map<Node, org.openstreetmap.josm.data.coor.LatLon> nodeOldCoors;
        private final Map<Node, org.openstreetmap.josm.data.coor.LatLon> nodeNewCoors;
        private final int changedCount;
        private final int deletedNodesCount;

        public BulkSimplifyCommand(DataSet ds, Map<Way, List<Node>> wayMap, Set<Node> toDelete, Map<Node, org.openstreetmap.josm.data.coor.LatLon> movedNodes) {
            super(ds);
            this.newWayNodes = wayMap;
            this.oldWayNodes = new HashMap<>();
            this.wayOldModified = new HashMap<>();
            this.nodesToDelete = toDelete;
            this.deletedNodesCount = toDelete.size();
            this.nodeOldCoors = new HashMap<>();
            this.nodeNewCoors = new HashMap<>();
            if (movedNodes != null) {
                for (Map.Entry<Node, org.openstreetmap.josm.data.coor.LatLon> entry : movedNodes.entrySet()) {
                    Node n = entry.getKey();
                    if (!toDelete.contains(n)) {
                        nodeOldCoors.put(n, n.getCoor());
                        nodeNewCoors.put(n, entry.getValue());
                    }
                }
            }
            int count = 0;
            for (Map.Entry<Way, List<Node>> entry : wayMap.entrySet()) {
                Way w = entry.getKey();
                List<Node> next = entry.getValue();
                if (!w.getNodes().equals(next)) {
                    oldWayNodes.put(w, new ArrayList<>(w.getNodes()));
                    wayOldModified.put(w, w.isModified());
                    count++;
                }
            }
            this.changedCount = count;
        }

        public boolean hasChanges() { return changedCount > 0 || !nodesToDelete.isEmpty() || !nodeOldCoors.isEmpty(); }
        public int getChangedCount() { return changedCount; }
        public int getDeletedNodesCount() { return deletedNodesCount; }

        @Override
        public boolean executeCommand() {
            DataSet ds = getAffectedDataSet();
            if (ds == null) return false;
            ds.beginUpdate();
            try {
                for (Map.Entry<Way, List<Node>> entry : oldWayNodes.entrySet()) {
                    Way w = entry.getKey();
                    w.setNodes(newWayNodes.get(w));
                    w.setModified(true);
                }
                for (Node n : nodesToDelete) n.setDeleted(true);
                for (Map.Entry<Node, org.openstreetmap.josm.data.coor.LatLon> entry : nodeNewCoors.entrySet()) {
                    entry.getKey().setCoor(entry.getValue());
                }
            } finally { ds.endUpdate(); }
            return true;
        }

        @Override
        public void undoCommand() {
            DataSet ds = getAffectedDataSet();
            if (ds == null) return;
            ds.beginUpdate();
            try {
                for (Node n : nodesToDelete) n.setDeleted(false);
                for (Map.Entry<Node, org.openstreetmap.josm.data.coor.LatLon> entry : nodeOldCoors.entrySet()) {
                    entry.getKey().setCoor(entry.getValue());
                }
                for (Map.Entry<Way, List<Node>> entry : oldWayNodes.entrySet()) {
                    Way w = entry.getKey();
                    w.setNodes(entry.getValue());
                    w.setModified(wayOldModified.getOrDefault(w, false));
                }
            } finally { ds.endUpdate(); }
        }

        @Override public void fillModifiedData(Collection<OsmPrimitive> m, Collection<OsmPrimitive> d, Collection<OsmPrimitive> a) { 
            m.addAll(oldWayNodes.keySet()); 
            m.addAll(nodeOldCoors.keySet());
            d.addAll(nodesToDelete); 
        }
        @Override public String getDescriptionText() { return tr("Adaptive Simplifier"); }
        @Override public Collection<OsmPrimitive> getParticipatingPrimitives() {
            Set<OsmPrimitive> all = new HashSet<>(oldWayNodes.keySet());
            all.addAll(nodesToDelete);
            all.addAll(nodeOldCoors.keySet());
            return all;
        }
    }
    private List<Node> simplifyPointByPoint(List<Node> nodes, double threshold) {
        int n = nodes.size();
        if (n < 3) return nodes;

        double[] ex = new double[n];
        double[] ey = new double[n];
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            ex[i] = nodes.get(i).getEastNorth().east();
            ey[i] = nodes.get(i).getEastNorth().north();
            if (ex[i] < minX) minX = ex[i]; if (ex[i] > maxX) maxX = ex[i];
            if (ey[i] < minY) minY = ey[i]; if (ey[i] > maxY) maxY = ey[i];
        }

        int gridDim = Math.max(2, (int) Math.sqrt(n));
        double cellW = (maxX - minX) / gridDim + 1e-9;
        double cellH = (maxY - minY) / gridDim + 1e-9;
        List<Integer>[] grid = new ArrayList[gridDim * gridDim];
        for (int i = 0; i < n - 1; i++) {
            int x1 = (int)((Math.min(ex[i], ex[i+1]) - minX) / cellW);
            int x2 = (int)((Math.max(ex[i], ex[i+1]) - minX) / cellW);
            int y1 = (int)((Math.min(ey[i], ey[i+1]) - minY) / cellH);
            int y2 = (int)((Math.max(ey[i], ey[i+1]) - minY) / cellH);
            for (int x = x1; x <= x2; x++) {
                for (int y = y1; y <= y2; y++) {
                    int c = x * gridDim + y;
                    if (grid[c] == null) grid[c] = new ArrayList<>();
                    grid[c].add(i);
                }
            }
        }

        int[] prev = new int[n];
        int[] next = new int[n];
        double[] imp = new double[n];
        boolean[] removed = new boolean[n];
        double dynAngleThr = Math.max(150.0, 180.0 - threshold * 5.0);

        PriorityQueue<Integer> pq = new PriorityQueue<>((a, b) -> {
            int cmp = Double.compare(imp[a], imp[b]);
            return (cmp != 0) ? cmp : Integer.compare(a, b);
        });

        for (int i = 0; i < n; i++) {
            prev[i] = i - 1;
            next[i] = i + 1;
            if (i > 0 && i < n - 1) {
                imp[i] = calculateImportance(i, prev[i], next[i], ex, ey, dynAngleThr);
                pq.add(i);
            }
        }

        while (!pq.isEmpty()) {
            int i = pq.poll();
            if (removed[i]) continue;

            double currentImp = calculateImportance(i, prev[i], next[i], ex, ey, dynAngleThr);
            if (Math.abs(currentImp - imp[i]) > 1e-15) {
                imp[i] = currentImp;
                pq.add(i);
                continue;
            }

            if (imp[i] >= threshold) continue;

            Node curr = nodes.get(i);
            if (curr.isTagged() || curr.getParentWays().size() > 1 || !OsmPrimitive.getParentRelations(Collections.singleton(curr)).isEmpty()) continue;

            int p = prev[i];
            int nx = next[i];
            if (causesSelfIntersection(p, nx, i, ex, ey, prev, next, removed, grid, minX, minY, cellW, cellH, gridDim)) continue;

            removed[i] = true;
            next[p] = nx;
            prev[nx] = p;

            if (p > 0) {
                imp[p] = calculateImportance(p, prev[p], next[p], ex, ey, dynAngleThr);
                pq.add(p);
            }
            if (nx < n - 1) {
                imp[nx] = calculateImportance(nx, prev[nx], next[nx], ex, ey, dynAngleThr);
                pq.add(nx);
            }
        }

        List<Node> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!removed[i]) result.add(nodes.get(i));
        }
        return result;
    }

    private double calculateImportance(int i, int p, int nx, double[] ex, double[] ey, double dynAngleThr) {
        double angle = calculateAngleRaw(ex[p], ey[p], ex[i], ey[i], ex[nx], ey[nx]);
        if (angle < dynAngleThr) return Double.MAX_VALUE;
        double area = Math.abs((ex[p] - ex[i]) * (ey[nx] - ey[i]) - (ey[p] - ey[i]) * (ex[nx] - ex[i])) / 2.0;
        double base = Math.hypot(ex[p] - ex[nx], ey[p] - ey[nx]);
        double normImp = (base > 1e-6) ? area / base : 0.0;
        double curv = Math.max(0.0, 180.0 - angle);
        double factor = (curv < 2.0) ? 1.4 : (curv < 5.0) ? 1.2 : (curv < 15.0) ? 1.0 : (curv < 30.0) ? 0.8 : 0.6;
        return normImp / factor;
    }

    private List<Node> mergeNearbyNodes(List<Node> nodes, double mergeThreshold, Map<Node, org.openstreetmap.josm.data.coor.LatLon> movedNodes) {
        if (nodes.size() < 2) return nodes;
        boolean isClosed = nodes.size() > 2 && nodes.get(0) == nodes.get(nodes.size() - 1);
        List<Node> result = new ArrayList<>(nodes);
        boolean changed;
        do {
            changed = false;
            for (int i = 1; i < result.size(); i++) {
                Node pNode = result.get(i - 1);
                Node cNode = result.get(i);
                double dist = pNode.getEastNorth().distance(cNode.getEastNorth());
                if (dist < mergeThreshold) {
                    boolean pAnchor = pNode.isTagged() || pNode.getParentWays().size() > 1;
                    boolean cAnchor = cNode.isTagged() || cNode.getParentWays().size() > 1;
                    if (pAnchor && cAnchor) continue;
                    
                    if (cAnchor) {
                        result.remove(i - 1);
                    } else {
                        result.remove(i);
                    }
                    changed = true;
                    break; 
                }
            }
        } while (changed);
        if (isClosed && result.size() >= 3) {
            Node first = result.get(0);
            Node last = result.get(result.size() - 1);
            Node origBound = nodes.get(0);
            if (first != origBound && last == origBound) {
                result.set(result.size() - 1, first);
            } else if (first == origBound && last != origBound) {
                result.add(first);
            }
        }
        return result;
    }

    private boolean causesSelfIntersection(int p, int nx, int i, double[] ex, double[] ey, int[] prev, int[] next, boolean[] removed, List<Integer>[] grid, double minX, double minY, double cellW, double cellH, int gridDim) {
        double ax = ex[p], ay = ey[p];
        double bx = ex[nx], by = ey[nx];

        double sMinX = Math.min(ax, bx), sMaxX = Math.max(ax, bx);
        double sMinY = Math.min(ay, by), sMaxY = Math.max(ay, by);

        int x1 = Math.max(0, Math.min(gridDim - 1, (int)((sMinX - minX) / cellW)));
        int x2 = Math.max(0, Math.min(gridDim - 1, (int)((sMaxX - minX) / cellW)));
        int y1 = Math.max(0, Math.min(gridDim - 1, (int)((sMinY - minY) / cellH)));
        int y2 = Math.max(0, Math.min(gridDim - 1, (int)((sMaxY - minY) / cellH)));

        Set<Integer> checkedSegments = new HashSet<>();

        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                int c = x * gridDim + y;
                List<Integer> cell = grid[c];
                if (cell == null) continue;
                for (int segStart : cell) {
                    int actP1 = segStart;
                    while (actP1 > 0 && removed[actP1]) {
                        actP1 = prev[actP1];
                    }
                    if (removed[actP1]) continue;
                    int actP2 = next[actP1];
                    if (actP2 >= ex.length) continue;

                    if (!checkedSegments.add(actP1)) continue;

                    if (actP1 == p || actP2 == p || actP1 == nx || actP2 == nx || actP1 == i || actP2 == i) {
                        continue;
                    }

                    double cx = ex[actP1], cy = ey[actP1];
                    double dx = ex[actP2], dy = ey[actP2];

                    if (segmentsIntersect(ax, ay, bx, by, cx, cy, dx, dy)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean segmentsIntersect(double a1x, double a1y, double a2x, double a2y, double b1x, double b1y, double b2x, double b2y) {
        double o1 = orientation(a1x, a1y, a2x, a2y, b1x, b1y);
        double o2 = orientation(a1x, a1y, a2x, a2y, b2x, b2y);
        double o3 = orientation(b1x, b1y, b2x, b2y, a1x, a1y);
        double o4 = orientation(b1x, b1y, b2x, b2y, a2x, a2y);

        if (((o1 > 0 && o2 < 0) || (o1 < 0 && o2 > 0)) && 
            ((o3 > 0 && o4 < 0) || (o3 < 0 && o4 > 0))) {
            return true;
        }

        if (o1 == 0 && onSegment(a1x, a1y, b1x, b1y, a2x, a2y)) return true;
        if (o2 == 0 && onSegment(a1x, a1y, b2x, b2y, a2x, a2y)) return true;
        if (o3 == 0 && onSegment(b1x, b1y, a1x, a1y, b2x, b2y)) return true;
        if (o4 == 0 && onSegment(b1x, b1y, a2x, a2y, b2x, b2y)) return true;

        return false;
    }

    private static double orientation(double px, double py, double qx, double qy, double rx, double ry) {
        double val = (qx - px) * (ry - py) - (qy - py) * (rx - px);
        double eps = 1e-11;
        return (val > eps) ? 1 : (val < -eps ? -1 : 0);
    }

    private static boolean onSegment(double px, double py, double qx, double qy, double rx, double ry) {
        return qx <= Math.max(px, rx) + 1e-11 && qx >= Math.min(px, rx) - 1e-11 &&
               qy <= Math.max(py, ry) + 1e-11 && qy >= Math.min(py, ry) - 1e-11;
    }

    private double calculateAngleRaw(double x1, double y1, double x2, double y2, double x3, double y3) {
        double a2 = Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2);
        double b2 = Math.pow(x2 - x3, 2) + Math.pow(y2 - y3, 2);
        double c2 = Math.pow(x3 - x1, 2) + Math.pow(y3 - y1, 2);
        if (a2 < 1e-18 || b2 < 1e-18) return 180.0;
        double cos = (a2 + b2 - c2) / (2 * Math.sqrt(a2 * b2));
        return Math.acos(Math.max(-1, Math.min(1, cos))) * 180 / Math.PI;
    }

    private void setupAutoClamp(JSpinner spinner, double min, double max, boolean isInteger) {
        JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) spinner.getEditor();
        JFormattedTextField field = editor.getTextField();
        javax.swing.Timer timer = new javax.swing.Timer(1000, e -> {
            try {
                String text = field.getText().trim().replace(',', '.');
                if (!text.isEmpty()) {
                    double dVal = Double.parseDouble(text);
                    if (isInteger) {
                        int iVal = (int) Math.round(dVal);
                        if (iVal < (int) min) {
                            spinner.setValue((int) min);
                        } else if (iVal > (int) max) {
                            spinner.setValue((int) max);
                        } else {
                            spinner.setValue(iVal);
                        }
                    } else {
                        if (dVal < min) {
                            spinner.setValue(min);
                        } else if (dVal > max) {
                            spinner.setValue(max);
                        } else {
                            spinner.setValue(dVal);
                        }
                    }
                }
            } catch (Exception ex) {
                try { spinner.commitEdit(); } catch (Exception ignored) {}
            }
        });
        timer.setRepeats(false);
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void trigger() { timer.restart(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { trigger(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { trigger(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { trigger(); }
        });
    }
}