package com.frostwire.android.gui.adapters.menu;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.media.ExifInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.util.StringUtils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;

/**
 * Created by Grzesiek on 2016-11-27.
 */

public class ShowFileDetailsAction extends MenuAction {
    private final FileDescriptor fd;

    private static final String FILE_SIZE = "fileSize";
    private static final String FILE_PATH = "filePath";


    public ShowFileDetailsAction(Context context, FileDescriptor fd) {
        super(context, R.drawable.contextmenu_icon, R.string.details);
        this.fd = fd;
    }

    @Override
    protected void onClick(Context context) {
        DetailsMenuActionDialog dialog = DetailsMenuActionDialog.newInstance(fd.filePath, fd.fileSize);
        dialog.show(((Activity) getContext()).getFragmentManager());
    }


    public static class DetailsMenuActionDialog extends AbstractDialog {


        public DetailsMenuActionDialog() {
            super(R.layout.dialog_default_info);
        }

        public static DetailsMenuActionDialog newInstance(String filePath, long size) {
            DetailsMenuActionDialog dialog = new DetailsMenuActionDialog();
            Bundle bundle = new Bundle();
            bundle.putString(FILE_PATH, filePath);
            bundle.putLong(FILE_SIZE, size);
            dialog.setArguments(bundle);
            return dialog;
        }

        @Override
        protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
            String filePath = getArgument(FILE_PATH);
            Long fileSize = getArgument(FILE_SIZE);

            TextView title = findView(dlg, R.id.dialog_default_info_title);
            title.setText(R.string.details);
            String name = FilenameUtils.getBaseName(filePath);
            String extension = FilenameUtils.getExtension(filePath);
            String sizeString = UIUtils.getBytesInHuman(fileSize);

            StringBuilder contentString = new StringBuilder();
            contentString.append(name).append("\n")
                    .append(extension).append("\n")
                    .append(sizeString);

            appendExifData(contentString, filePath);

            TextView contents = findView(dlg, R.id.dialog_default_info_text);
            contents.setText(contentString.toString());


            Button okButton = findView(dlg, R.id.dialog_default_info_button_ok);
            okButton.setText(android.R.string.ok);

            okButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dismiss();
                }
            });
        }

        private void appendExifData(StringBuilder sb, String filePath) {

            try {
                ExifInterface exif = new ExifInterface(filePath);

                int length = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0);
                int width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0);
                if (length > 0 && width > 0) {
                    int imageOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
                    if (imageOrientation == ExifInterface.ORIENTATION_ROTATE_90 || imageOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
                        //portrait lxw
                        sb.append("\n").append(length).append(" x ").append(width);
                    } else if (imageOrientation != ExifInterface.ORIENTATION_UNDEFINED) {
                        //landscape wxl
                        sb.append("\n").append(width).append(" x ").append(length);
                    } //undefined is ignored
                }

                if (!StringUtils.isNullOrEmpty(exif.getAttribute(ExifInterface.TAG_ISO))) {
                    sb.append("\n").append(exif.getAttribute(ExifInterface.TAG_ISO));
                }
                if (!StringUtils.isNullOrEmpty(exif.getAttribute(ExifInterface.TAG_APERTURE))) {
                    sb.append("\n").append(exif.getAttribute(ExifInterface.TAG_APERTURE));
                }

                if (!StringUtils.isNullOrEmpty(exif.getAttribute(ExifInterface.TAG_DATETIME))) {
                    sb.append("\n").append(exif.getAttribute(ExifInterface.TAG_DATETIME));
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

}
