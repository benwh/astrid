package com.todoroo.astrid.tags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.timsu.astrid.R;
import com.todoroo.andlib.data.AbstractModel;
import com.todoroo.andlib.data.TodorooCursor;
import com.todoroo.andlib.service.DependencyInjectionService;
import com.todoroo.andlib.utility.DateUtilities;
import com.todoroo.astrid.data.Metadata;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.tags.TagService.Tag;
import com.todoroo.astrid.ui.PopupControlSet;
import com.todoroo.astrid.utility.Flags;

/**
 * Control set to manage adding and removing tags
 *
 * @author Tim Su <tim@todoroo.com>
 *
 */
public final class TagsControlSet extends PopupControlSet {

    // --- instance variables

    //private final Spinner tagSpinner;
    //@Autowired private TagDataService tagDataService;
    private final TagService tagService = TagService.getInstance();
    private final Tag[] allTags;
    private final String[] allTagNames;

    private final LinearLayout newTags;
    private final ListView selectedTags;
    private boolean populated = false;
    private final HashMap<String, Integer> tagIndices;

    //private final LinearLayout tagsContainer;
    private final Activity activity;
    private final TextView tagsDisplay;

    public TagsControlSet(Activity activity, int viewLayout, int displayViewLayout, int title) {
        super(activity, viewLayout, displayViewLayout, title);
        DependencyInjectionService.getInstance().inject(this);
        this.activity = activity;
        allTags = getTagArray();
        allTagNames = getTagNames(allTags);
        tagIndices = buildTagIndices(allTagNames);

        selectedTags = (ListView) getView().findViewById(R.id.existingTags);
        selectedTags.setAdapter(new ArrayAdapter<String>(activity,
                android.R.layout.simple_list_item_multiple_choice, allTagNames));
        selectedTags.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        this.newTags = (LinearLayout) getView().findViewById(R.id.newTags);

        tagsDisplay = (TextView) getDisplayView().findViewById(R.id.tags_display);
    }

    private Tag[] getTagArray() {
        ArrayList<Tag> tagsList = TagService.getInstance().getTagList();
        return tagsList.toArray(new Tag[tagsList.size()]);
    }

    private HashMap<String, Integer> buildTagIndices(String[] tagNames) {
        HashMap<String, Integer> indices = new HashMap<String, Integer>();
        for (int i = 0; i < tagNames.length; i++) {
            indices.put(tagNames[i], i);
        }
        return indices;
    }

    private String[] getTagNames(Tag[] tags) {
        String[] names = new String[tags.length];
        for (int i = 0; i < tags.length; i++) {
            names[i] = tags[i].toString();
        }
        return names;
    }

    private String buildTagString() {
        StringBuilder builder = new StringBuilder();

        LinkedHashSet<String> tags = getTagSet();
        for (String tag : tags) {
            if (builder.length() != 0)
                builder.append(", ");
            builder.append(tag);
        }

        if (builder.length() == 0)
            builder.append(activity.getString(R.string.TEA_tags_none));
        return builder.toString();
    }


    private void setTagSelected(String tag) {
        int index = tagIndices.get(tag);
        selectedTags.setItemChecked(index, true);
    }

    private LinkedHashSet<String> getTagSet() {
        LinkedHashSet<String> tags = new LinkedHashSet<String>();
        for(int i = 0; i < selectedTags.getAdapter().getCount(); i++) {
            if (selectedTags.isItemChecked(i))
                tags.add(allTagNames[i]);
        }

        for(int i = 0; i < newTags.getChildCount(); i++) {
            TextView tagName = (TextView) newTags.getChildAt(i).findViewById(R.id.text1);
            if(tagName.getText().length() == 0)
                continue;

            tags.add(tagName.getText().toString());
        }
        return tags;
    }

    /** Adds a tag to the tag field */
    boolean addTag(String tagName, boolean reuse) {
        LayoutInflater inflater = activity.getLayoutInflater();

        // check if already exists
        TextView lastText = null;
        for(int i = 0; i < newTags.getChildCount(); i++) {
            View view = newTags.getChildAt(i);
            lastText = (TextView) view.findViewById(R.id.text1);
            if(lastText.getText().equals(tagName))
                return false;
        }

        final View tagItem;
        if(reuse && lastText != null && lastText.getText().length() == 0) {
            tagItem = (View) lastText.getParent();
        } else {
            tagItem = inflater.inflate(R.layout.tag_edit_row, null);
            newTags.addView(tagItem);
        }
        if(tagName == null)
            tagName = ""; //$NON-NLS-1$

        final AutoCompleteTextView textView = (AutoCompleteTextView)tagItem.
            findViewById(R.id.text1);
        textView.setText(tagName);

        textView.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                //
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {
                //
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                    int count) {
                if(count > 0 && newTags.getChildAt(newTags.getChildCount()-1) ==
                        tagItem)
                    addTag("", false); //$NON-NLS-1$
            }
        });

        textView.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView arg0, int actionId, KeyEvent arg2) {
                if(actionId != EditorInfo.IME_NULL)
                    return false;
                if(getLastTextView().getText().length() != 0) {
                    addTag("", false); //$NON-NLS-1$
                }
                return true;
            }
        });

        ImageButton reminderRemoveButton;
        reminderRemoveButton = (ImageButton)tagItem.findViewById(R.id.button1);
        reminderRemoveButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                TextView lastView = getLastTextView();
                if(lastView == textView && textView.getText().length() == 0)
                    return;

                if(newTags.getChildCount() > 1)
                    newTags.removeView(tagItem);
                else
                    textView.setText(""); //$NON-NLS-1$
            }
        });

        return true;
    }

    /**
     * Get tags container last text view. might be null
     * @return
     */
    private TextView getLastTextView() {
        if(newTags.getChildCount() == 0)
            return null;
        View lastItem = newTags.getChildAt(newTags.getChildCount()-1);
        TextView lastText = (TextView) lastItem.findViewById(R.id.text1);
        return lastText;
    }

    @Override
    public void readFromTask(Task task) {
        newTags.removeAllViews();

        if(task.getId() != AbstractModel.NO_ID) {
            TodorooCursor<Metadata> cursor = tagService.getTags(task.getId());
            try {
                for(cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    String tag = cursor.get(TagService.TAG);
                    setTagSelected(tag);
                }
            } finally {
                cursor.close();
            }
        }
        addTag("", false);
        refreshDisplayView();
        populated = true;
    }

    @Override
    public String writeToModel(Task task) {
        // this is a case where we're asked to save but the UI was not yet populated
        if(!populated)
            return null;

        LinkedHashSet<String> tags = getTagSet();

        if(TagService.getInstance().synchronizeTags(task.getId(), tags)) {
            Flags.set(Flags.TAGS_CHANGED);
            task.setValue(Task.MODIFICATION_DATE, DateUtilities.now());
        }

        return null;
    }

    @Override
    protected void refreshDisplayView() {
        tagsDisplay.setText(buildTagString());
    }

}
