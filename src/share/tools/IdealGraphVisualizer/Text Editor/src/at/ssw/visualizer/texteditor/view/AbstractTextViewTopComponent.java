package at.ssw.visualizer.texteditor.view;

import at.ssw.visualizer.core.selection.Selection;
import at.ssw.visualizer.core.selection.SelectionManager;
import at.ssw.visualizer.texteditor.EditorKit;
import com.sun.hotspot.igv.data.InputBlock;
import com.sun.hotspot.igv.data.InputGraph;
import java.awt.BorderLayout;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.windows.TopComponent;

/**
 *
 * @author Alexander Reder
 */
public abstract class AbstractTextViewTopComponent extends TopComponent {
    
    protected InputGraph curCFG;
    protected InputBlock[] curBlocks;

    private JEditorPane editorPane;
    
    // EditorKit must be set in the imlementation class
    public AbstractTextViewTopComponent(EditorKit kit) {
        editorPane = new JEditorPane();
        editorPane.setEditorKit(kit);
        editorPane.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        setLayout(new BorderLayout());
        add(scrollPane);
    }

    @Override
    protected void componentShowing() {
        super.componentShowing();
        SelectionManager.getDefault().addChangeListener(selectionChangeListener);
        updateContent();
    }

    @Override
    protected void componentHidden() {
        super.componentHidden();
        SelectionManager.getDefault().removeChangeListener(selectionChangeListener);
        curCFG = null;
        curBlocks = null;
    }


    private ChangeListener selectionChangeListener = new ChangeListener() {
        public void stateChanged(ChangeEvent event) {
            updateContent();
        }
    };

    protected void updateContent() {
        Selection selection = SelectionManager.getDefault().getCurSelection();
        InputGraph newCFG = selection.get(InputGraph.class);
        InputBlock[] newBlocks = selection.get(InputBlock[].class);

        if (newCFG == null || newBlocks == null || newBlocks.length == 0) {
            editorPane.setText("No block selected\n");
        } else if (curCFG != newCFG || !Arrays.equals(curBlocks, newBlocks)) {
            editorPane.setText(getContent(newCFG, newBlocks));
        }
        curCFG = newCFG;
        curBlocks = newBlocks;
    }

    protected abstract String getContent(InputGraph cfg, InputBlock[] blocks);
    
}
