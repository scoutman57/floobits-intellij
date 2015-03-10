package floobits.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import floobits.common.Constants;
import floobits.common.HighlightContext;
import floobits.common.dmp.FlooPatchPosition;
import floobits.common.interfaces.IDoc;
import floobits.common.protocol.FlooUser;
import floobits.common.protocol.handlers.FlooHandler;
import floobits.utilities.Colors;
import floobits.utilities.Flog;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;


public class DocImpl extends IDoc {

    private final ContextImpl context;
    private final Document document;
    public final static HashMap<Integer, HashMap<String, LinkedList<RangeHighlighter>>> highlights = new HashMap<Integer, HashMap<String, LinkedList<RangeHighlighter>>>();

    public DocImpl(ContextImpl context, Document document) {
        this.context = context;
        this.document = document;
    }

    public String toString() {
        return document.toString();
    }

    protected LinkedList<RangeHighlighter> getHighlightsForUser(String path, int userID) {
        HashMap<String, LinkedList<RangeHighlighter>> integerRangeHighlighterHashMap = highlights.get(userID);
        if (integerRangeHighlighterHashMap == null) {
            return null;
        }
        final LinkedList<RangeHighlighter> rangeHighlighters = integerRangeHighlighterHashMap.get(path);
        if (rangeHighlighters == null) {
            return null;
        }
        return rangeHighlighters;
    }

    protected void removeHighlights(MarkupModel markupModel, LinkedList<RangeHighlighter> appliedHighlighters){
        if (appliedHighlighters == null) {
            return;
        }
        RangeHighlighter[] existingHighlighters = markupModel.getAllHighlighters();
        for (RangeHighlighter rangeHighlighter: appliedHighlighters) {
            for (RangeHighlighter markupHighlighter : existingHighlighters) {
                if (rangeHighlighter == markupHighlighter) {
                    markupModel.removeHighlighter(rangeHighlighter);
                }
            }
        }
    }

    @Override
    public void removeHighlight(Integer userId, final String path) {
        HashMap<String, LinkedList<RangeHighlighter>> integerRangeHighlighterHashMap = highlights.get(userId);
        if (integerRangeHighlighterHashMap == null) {
            return;
        }
        final LinkedList<RangeHighlighter> rangeHighlighters = integerRangeHighlighterHashMap.get(path);
        if (rangeHighlighters == null) {
            return;
        }
        Editor[] editors = EditorFactory.getInstance().getEditors(document, context.project);
        for (Editor editor : editors) {
            if (editor.isDisposed()) {
                continue;
            }
            removeHighlights(editor.getMarkupModel(), rangeHighlighters);
        }
        rangeHighlighters.clear();
    }

    protected void applyHighlight_(HighlightContext highlight) {
        final TextAttributes attributes = new TextAttributes();
        final JBColor color = Colors.getColorForUser(highlight.username);
        final ContextImpl context = (ContextImpl) highlight.context;
        FlooHandler handler = highlight.context.getFlooHandler();
        if (handler == null) {
            return;
        }
        attributes.setEffectColor(color);
        attributes.setEffectType(EffectType.SEARCH_MATCH);
        attributes.setBackgroundColor(color);
        attributes.setForegroundColor(Colors.getFGColor());
        for (Balloon balloon : context.balloons) {
            balloon.setAnimationEnabled(false);
            balloon.dispose();
        }
        context.balloons.clear();

        int textLength = highlight.textLength;
        int userID = highlight.userid;
        LinkedList<RangeHighlighter> appliedHighlighters = getHighlightsForUser(highlight.path, userID);
        LinkedList<RangeHighlighter> newHighlighters = new LinkedList<RangeHighlighter>();

        boolean first = true;
        Editor[] editors = EditorFactory.getInstance().getEditors(document, context.project);

        for (List<Integer> range : highlight.ranges) {
            int start = range.get(0);
            int end = range.get(1);
            if (start == end) {
                end += 1;
            }
            if (end > textLength) {
                end = textLength;
            }
            if (start >= textLength) {
                start = textLength - 1;
            }
            for (final Editor editor : editors) {
                if (editor.isDisposed()) {
                    continue;
                }
                final MarkupModel markupModel = editor.getMarkupModel();
                removeHighlights(markupModel, appliedHighlighters);

                RangeHighlighter rangeHighlighter = markupModel.addRangeHighlighter(start, end, HighlighterLayer.ERROR + 100, attributes, HighlighterTargetArea.EXACT_RANGE);

                newHighlighters.add(rangeHighlighter);
                CaretModel caretModel = editor.getCaretModel();
                LogicalPosition position = editor.offsetToLogicalPosition(start);
                final Point p = editor.visualPositionToXY(new VisualPosition(position.line, 0));
                final String htmlText = String.format("<p>%s</p>", highlight.username);
                ContextImpl.BalloonState balloonState = context.gravatars.get(highlight.gravatar);
                if (balloonState != null) {
                    int previousLine;
                    Image img;
                    img = balloonState.gravatar;
                    previousLine = balloonState.lineNumber;
                    final Color newColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 255);
                    if (previousLine != position.line && !handler.state.username.equals(highlight.username) && img != null) {
                        final Image gravatarImg = img;
                        balloonState.lineNumber = position.line;
                        ApplicationManager.getApplication().invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                if (context.getFlooHandler() == null) {
                                    return;
                                }
                                Balloon balloon = JBPopupFactory.getInstance()
                                        .createHtmlTextBalloonBuilder(htmlText, new ImageIcon(gravatarImg), newColor, null)
                                        .setFadeoutTime(1000)
                                        .createBalloon();
                                balloon.setAnimationEnabled(false);
                                balloon.show(new RelativePoint(editor.getContentComponent(), p), Balloon.Position.atLeft);
                                balloon.setAnimationEnabled(true);
                                context.balloons.add(balloon);
                            }
                        });
                    }
                }
                if (first) {
                    first = false;
                    if (highlight.force) {
                        caretModel.moveToOffset(start);
                        position = caretModel.getLogicalPosition();
                        ScrollingModel scrollingModel = editor.getScrollingModel();
                        scrollingModel.scrollTo(position, ScrollType.MAKE_VISIBLE);
                    }
                }
            }
            HashMap<String, LinkedList<RangeHighlighter>> integerRangeHighlighterHashMap = highlights.get(userID);

            if (integerRangeHighlighterHashMap == null) {
                integerRangeHighlighterHashMap = new HashMap<String, LinkedList<RangeHighlighter>>();
                highlights.put(userID, integerRangeHighlighterHashMap);
            }
            integerRangeHighlighterHashMap.put(highlight.path, newHighlighters);
        }
    }

    @Override
    public void applyHighlight(HighlightContext highlight) {
        final FileEditorManager manager = FileEditorManager.getInstance(context.project);
        final VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);

        if ((highlight.force || highlight.following) && virtualFile != null && virtualFile.isValid()) {
            boolean spam = false;
            if (!manager.isFileOpen(virtualFile) || !Arrays.asList(manager.getSelectedFiles()).contains(virtualFile)) {
                spam = true;
            }
            if (spam && highlight.username.length() > 0 && highlight.force) {
                context.statusMessage(String.format("%s has summoned you!", highlight.username));
            }
            manager.openFile(virtualFile, false, true);
        }

        highlight.textLength = document.getTextLength();
        if (highlight.textLength == 0) {
            return;
        }
        synchronized (context) {
            try {
                context.setListener(false);
                highlight.force = highlight.force || highlight.following;
                highlight.context = context;
                applyHighlight_(highlight);
            } catch (Throwable e) {
                Flog.warn(e);
            } finally {
                context.setListener(true);
            }
        }
    }

    @Override
    public void save() {
        if (context.project == null) {
            Flog.info("Document: %s can not be saved.", document);
            return;
        }
        if (!ReadonlyStatusHandler.ensureDocumentWritable(context.project, document)) {
            Flog.info("Document: %s is not writable, can not save.", document);
            return;
        }
        setReadOnly(false);
        FileDocumentManager.getInstance().saveDocument(document);

    }

    public void setText(final String text) {
        document.setText(text);
    }

    @Override
    public String getText() {
        return document.getText();
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        document.setReadOnly(readOnly);
    }

    @Override
    public boolean makeWritable() {
        if (!document.isWritable()) {
            document.setReadOnly(false);
        }
        return ReadonlyStatusHandler.ensureDocumentWritable(context.project, document);
    }

    @Override
    public FileImpl getVirtualFile() {
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        if (file == null) {
            return null;
        }
        return new FileImpl(file);
    }

    public String patch(FlooPatchPosition[] positions) {

        final Editor[] editors = EditorFactory.getInstance().getEditors(document, context.project);
        final HashMap<ScrollingModel, Integer[]> original = new HashMap<ScrollingModel, Integer[]>();
        for (Editor editor : editors) {
            if (editor.isDisposed()) {
                continue;
            }
            ScrollingModel scrollingModel = editor.getScrollingModel();
            original.put(scrollingModel, new Integer[]{scrollingModel.getHorizontalScrollOffset(), scrollingModel.getVerticalScrollOffset()});
        }
        for (FlooPatchPosition flooPatchPosition : positions) {
            final int start = Math.max(0, flooPatchPosition.start);
            int end_ld = Math.max(start + flooPatchPosition.end, start);
            end_ld = Math.min(end_ld, document.getTextLength());
            final String contents = Constants.NEW_LINE.matcher(flooPatchPosition.text).replaceAll("\n");
            final int finalEnd_ld = end_ld;
            synchronized (context) {
                try {
                    context.setListener(false);
                    document.replaceString(start, finalEnd_ld, contents);
                } catch (Throwable e) {
                    Flog.warn(e);
                } finally {
                    context.setListener(true);
                }
            }
        }
        String text = document.getText();
        for (Map.Entry<ScrollingModel, Integer[]> entry : original.entrySet()) {
            ScrollingModel model = entry.getKey();
            Integer[] offsets = entry.getValue();
            model.scrollHorizontally(offsets[0]);
            model.scrollVertically(offsets[1]);
        }
        return text;
    }
}
