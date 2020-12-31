package com.david.action;

import com.david.bean.StringEntity;
import com.david.utils.FileUtils;
import com.google.common.collect.Lists;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * layout文件夹转成strings
 * Created by zz on 2017/9/20.
 */
public class LayoutDirAction extends AnAction {

    private int index = 0;

    @Override
    public void actionPerformed(AnActionEvent e) {

        VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
        if (file == null) {
            showError("找不到目标文件");
            return;
        }

        if (!file.isDirectory()) {
            showError("请选择layout文件夹");
            return;
        } else if (!file.getName().startsWith("layout")) {
            showError("请选择layout文件夹");
            return;
        }

        VirtualFile[] children = file.getChildren();//获取layout文件夹下面的文件

        StringBuilder sb = new StringBuilder();

        for (VirtualFile child : children) { //遍历所有layout文件，然后获取其中的字串写到stringbuilder里面去
            layoutChild(child, sb);
        }

        VirtualFile resDir = file.getParent();//获取layout文件夹的父文件夹，看是不是res
        //获取res文件夹下面的values
        if (resDir.getName().equalsIgnoreCase("res")) {
            VirtualFile[] chids = resDir.getChildren(); //获取res文件夹下面文件夹列表
            for (VirtualFile chid : chids) { //遍历寻找values文件夹下面的strings文件
                if (chid.getName().startsWith("values")) {
                    if (chid.isDirectory()) {
                        VirtualFile[] values = chid.getChildren();
                        for (VirtualFile value : values) {
                            if (value.getName().startsWith("strings")) { //找到第一个strings文件
                                try {
                                    String content = new String(value.contentsToByteArray(), "utf-8"); //源文件内容
                                    System.out.println("utf-8=" + content);
                                    String result = content.replace("</resources>", sb.toString() + "\n</resources>"); //在最下方加上新的字串
                                    FileUtils.replaceContentToFile(value.getPath(), result);//替换文件
                                } catch (IOException e1) {
                                    e1.printStackTrace();
                                    showError(e1.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        }

        e.getActionManager().getAction(IdeActions.ACTION_SYNCHRONIZE).actionPerformed(e);
    }

    private void layoutChild(VirtualFile file, StringBuilder sb) {
        index = 0;

        String extension = file.getExtension();
        if (extension != null && extension.equalsIgnoreCase("xml")) {
            if (!file.getParent().getName().startsWith("layout")) {
                showError("请选择布局文件");
                return;
            }
        }

//        showHint(file.getName());
        List<StringEntity> strings;
        StringBuilder oldContent = new StringBuilder();
        try {
            oldContent.append(new String(file.contentsToByteArray(), "utf-8"));
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        InputStream is = null;
        try {
            is = file.getInputStream();
            strings = extraStringEntity(is, file.getNameWithoutExtension().toLowerCase(), oldContent);
            if (strings != null) {
                for (StringEntity string : strings) {
                    sb.append("\n    <string name=\"" + string.getId() + "\">" + string.getValue() + "</string>");
                }
                FileUtils.replaceContentToFile(file.getPath(), oldContent.toString());
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        } finally {
            FileUtils.closeQuietly(is);
        }

    }

    private List<StringEntity> extraStringEntity(InputStream is, String fileName, StringBuilder oldContent) {
        List<StringEntity> strings = Lists.newArrayList();
        try {
            return generateStrings(DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is), strings, fileName, oldContent);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private List<StringEntity> generateStrings(Node node, List<StringEntity> strings, String fileName, StringBuilder oldContent) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Node stringNode = node.getAttributes().getNamedItem("android:text");
            if (stringNode != null) {
                String value = stringNode.getNodeValue();
                if (!value.contains("@string")) {
                    final String id = fileName + "_text_" + (index++);
                    strings.add(new StringEntity(id, value));
                    String newContent = oldContent.toString().replaceFirst("\"" + value + "\"", "\"@string/" + id + "\"");
                    oldContent = oldContent.replace(0, oldContent.length(), newContent);
                }
            }
            Node hintNode = node.getAttributes().getNamedItem("android:hint");
            if (hintNode != null) {
                String value = hintNode.getNodeValue();
                if (!value.contains("@string")) {
                    final String id = fileName + "_hint_text_" + (index++);
                    strings.add(new StringEntity(id, value));
                    String newContent = oldContent.toString().replaceFirst("\"" + value + "\"", "\"@string/" + id + "\"");
                    oldContent = oldContent.replace(0, oldContent.length(), newContent);
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int j = 0; j < children.getLength(); j++) {
            generateStrings(children.item(j), strings, fileName, oldContent);
        }
        return strings;
    }


    private void showHint(String msg) {
        Notifications.Bus.notify(new Notification("DavidString", "DavidString", msg, NotificationType.WARNING));
    }

    private void showError(String msg) {
        Notifications.Bus.notify(new Notification("DavidString", "DavidString", msg, NotificationType.ERROR));
    }
}
