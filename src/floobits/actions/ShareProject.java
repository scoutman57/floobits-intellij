package floobits.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import floobits.FloobitsPlugin;

public class ShareProject extends AnAction {

    public void actionPerformed(AnActionEvent e) {

        FloobitsPlugin.shareProject(e.getProject());
    }
}