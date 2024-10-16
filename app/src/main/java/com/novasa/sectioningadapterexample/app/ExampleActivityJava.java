package com.novasa.sectioningadapterexample.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.novasa.sectioningadapter.SectioningAdapter;
import com.novasa.sectioningadapterexample.R;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import kotlin.Unit;

@SuppressWarnings("FieldCanBeLocal")
public class ExampleActivityJava extends AppCompatActivity {

    private static final int SECTION_COUNT = 5;
    private static final int ITEM_COUNT = 20;

    private final Random mRng = new Random();

    private Adapter mAdapter;

    private List<Item> mItems;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mItems = new ArrayList<>(ITEM_COUNT);
        for (int i = 0; i < ITEM_COUNT; i++) {
            mItems.add(new Item(i, mRng.nextInt(SECTION_COUNT) + 1));
        }

        final RecyclerView recyclerView = findViewById(R.id.recyclerView);

        mAdapter = new Adapter();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(mAdapter);

        mAdapter.setItems(mItems);
        mAdapter.forEach((item, integer, integer2) -> Unit.INSTANCE);
    }

    class Item {
        int id;
        int section;

        Item(int id, int section) {
            this.id = id;
            this.section = section;
        }

        @SuppressLint("DefaultLocale")
        @NonNull
        @Override
        public String toString() {
            return String.format("id: %d, section: %d", id, section);
        }
    }

    class Adapter extends SectioningAdapter<Item, Integer> {

        @Nullable
        @Override
        protected Integer getSectionKeyForItem(@NotNull Item item) {
            return item.section;
        }

        @NotNull
        @Override
        public BaseViewHolder onCreateViewHolder(@NotNull Context context, @NotNull ViewGroup parent, int viewType) {
            final LayoutInflater inflater = LayoutInflater.from(context);
            return new ItemViewHolder(inflater.inflate(R.layout.cell_item_1, parent, false));
        }

        @Override
        protected int getHeaderViewTypeForSection(@NotNull Integer integer, int headerIndex) {
            return super.getHeaderViewTypeForSection(integer, headerIndex);
        }


        class ItemViewHolder extends SectionItemViewHolder {

            private final TextView title;

            ItemViewHolder(@NotNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.itemTitle);
            }

            @SuppressLint("DefaultLocale")
            @Override
            public void bind(int adapterPosition, @NotNull Integer integer, @NotNull Item item) {
                title.setText(String.format("Item: %d, section: %d", item.id, item.section));
            }

        }
    }
}
