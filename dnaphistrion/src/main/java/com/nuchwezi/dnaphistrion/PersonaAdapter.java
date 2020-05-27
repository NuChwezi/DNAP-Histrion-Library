package com.nuchwezi.dnaphistrion;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created by NemesisFixx on 23-Aug-16.
 */
public class PersonaAdapter extends BaseExpandableListAdapter {

    private ArrayList<JSONObject> personasList = new ArrayList<JSONObject>();
    private LayoutInflater mInflater;
    private Context context;
    ManagePersonaActivity.PersonaUUIDRunnable runnableSetActivePersona;
    ManagePersonaActivity.PersonaUUIDRunnable runnableDeletePersona;


    public PersonaAdapter(Context context, ArrayList<JSONObject> personasList, ManagePersonaActivity.PersonaUUIDRunnable runnableSetActivePersona, ManagePersonaActivity.PersonaUUIDRunnable runnableDeletePersona) {
        this.context = context;
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.personasList = personasList;
        this.runnableDeletePersona = runnableDeletePersona;
        this.runnableSetActivePersona = runnableSetActivePersona;
    }

    @Override
    public int getGroupCount() {
        return personasList.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return 1;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return personasList.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return personasList.get(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return 0;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        GroupViewHolder holder;
        if (convertView == null) {
            holder = new GroupViewHolder();
            convertView = mInflater.inflate(R.layout.dnap_persona_header_preview, null);
            holder.txtPersonaName = (TextView)convertView.findViewById(R.id.txtPersonaName);
            holder.personaContainer = (LinearLayout) convertView.findViewById(R.id.personaContainer);

            convertView.setTag(holder);
        } else {
            holder = (GroupViewHolder)convertView.getTag();
        }

        JSONObject persona = personasList.get(groupPosition);

        int appThemeColor = Persona.getAppThemeColor(persona);
        int complimentaryColor = Utility.getContrastVersionForColor(appThemeColor);
        int contrastingColor = Utility.getContrastVersionForColor(appThemeColor);

        holder.personaContainer.setBackgroundColor(appThemeColor);

        holder.txtPersonaName.setText(Persona.getAppName(persona));
        holder.txtPersonaName.setTextColor(complimentaryColor);

        GradientDrawable shape =  new GradientDrawable();
        shape.setCornerRadius( 6 );
        shape.setColor(Utility.getContrastVersionForColor(complimentaryColor));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            holder.txtPersonaName.setBackground(shape);
        }else {
            holder.txtPersonaName.setBackgroundDrawable(shape);
        }
        holder.txtPersonaName.setPadding(5,5,5,5);

        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            holder = new ViewHolder();
            convertView = mInflater.inflate(R.layout.dnap_persona_preview, null);
            //holder.txtPersonaName = (TextView)convertView.findViewById(R.id.txtPersonaName);
            holder.txtPersonaDescription = (TextView)convertView.findViewById(R.id.txtPersonaDescription);
            holder.txtPersonaTransport = (TextView)convertView.findViewById(R.id.txtPersonaTransport);
            holder.txtPersonaUUID = (TextView)convertView.findViewById(R.id.txtPersonaUUID);
            holder.imgPersonaBrandImage = (ImageView) convertView.findViewById(R.id.imgPersonaBrandImage);
            holder.personaContainer = (LinearLayout) convertView.findViewById(R.id.personaContainer);

            holder.btnDelete = (Button) convertView.findViewById(R.id.btnDelete);
            holder.btnMakeDefault = (Button) convertView.findViewById(R.id.btnMakeDefault);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder)convertView.getTag();
        }

        JSONObject persona = personasList.get(groupPosition);


        int appThemeColor = Persona.getAppThemeColor(persona);
        int complimentaryColor = Utility.getContrastVersionForColor(appThemeColor);
        int contrastingColor = Utility.getContrastVersionForColor(complimentaryColor);

        holder.personaContainer.setBackgroundColor(complimentaryColor);

        holder.txtPersonaDescription.setText(Persona.getAppDescription(persona));
        holder.txtPersonaDescription.setTextIsSelectable(true);
        holder.txtPersonaDescription.setTextColor(contrastingColor);

       /* holder.txtPersonaTransport.setText(String.format("%s | %s", Persona.getAppTransportMode(persona), Persona.getAppTheatreAddress(persona)));
        holder.txtPersonaTransport.setTextIsSelectable(true);
        holder.txtPersonaTransport.setTextColor(contrastingColor);*/

        holder.txtPersonaUUID.setText(String.format("UUID: %s",Persona.getAppUUID(persona)));
        holder.txtPersonaUUID.setTextIsSelectable(true);
        holder.txtPersonaUUID.setTextColor(contrastingColor);

        String uri = Persona.getAppBrandURI(persona);
        if((uri != null) && (uri.length() > 0)) {

            Picasso.with(context)
                    .load(uri)
                    .placeholder(R.drawable.loading)
                    .error(R.drawable.ic_persona)
                    .into(holder.imgPersonaBrandImage);
        }else {
            Picasso.with(context)
                    .load(R.drawable.ic_persona)
                    .placeholder(R.drawable.loading)
                    .error(R.drawable.ic_persona)
                    .into(holder.imgPersonaBrandImage);
        }

        // so we can know which persona to act on...
        holder.btnMakeDefault.setTag(Persona.getAppUUID(persona));
        holder.btnDelete.setTag(Persona.getAppUUID(persona));

        holder.btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runnableDeletePersona.run((String) v.getTag());
            }
        });

        holder.btnMakeDefault.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runnableSetActivePersona.run((String) v.getTag());
            }
        });

        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return false;
    }

    public static class ViewHolder {
        public LinearLayout personaContainer;
        //public TextView txtPersonaName;
        public TextView txtPersonaDescription;
        public TextView txtPersonaTransport;
        public TextView txtPersonaUUID;
        public ImageView imgPersonaBrandImage;
        public Button btnDelete, btnMakeDefault;
    }

    public static class GroupViewHolder {
        public LinearLayout personaContainer;
        public TextView txtPersonaName;
    }
}