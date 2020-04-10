package com.example.favouriteplacesapplication;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.MimeTypeMap;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;
import com.google.firebase.storage.UploadTask;
import com.google.firebase.Timestamp;
import org.w3c.dom.Document;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class FavouritePlaces extends AppCompatActivity implements OpenDialogBox.SendDataToFavouritePlace {

    RecyclerView recyclerView;
    FirebaseUser firebaseUser;
    FirebaseFirestore firebaseFirestore;
    DocumentReference documentReference;
    StorageReference storageReference;
    StorageReference fileReference;
    StorageTask storageTask;

    ProgressBar progressBar;

    List<String> nameOfFavPlaces;
    List<String> dateVisitedOfFavPlaces;
    List<String> imageUrlOfFavPlaces;
    List<String> addressOfFavPlaces;

    String userId;
    long favPlaceCount;
    double favPlaceLat;
    double favPlaceLon;
    Uri imageUri;
    String nameOfPlace;
    Date currentDate;
    String imageUrl;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);//will hide the title
        getSupportActionBar().hide(); //hide the title bar
        setContentView(R.layout.activity_favourite_places);

        progressBar = findViewById(R.id.progressBar);
        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        assert firebaseUser != null;
        userId = firebaseUser.getUid();
        firebaseFirestore = FirebaseFirestore.getInstance();
        documentReference = firebaseFirestore.collection("users").document(userId);
        storageReference = FirebaseStorage.getInstance().getReference(userId);

        recyclerView = findViewById(R.id.recyclerView);

        nameOfFavPlaces = new ArrayList<>();
        addressOfFavPlaces = new ArrayList<>();
        dateVisitedOfFavPlaces = new ArrayList<>();
        imageUrlOfFavPlaces = new ArrayList<>();

        documentReference.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                if(e != null){
                    Toast.makeText(FavouritePlaces.this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        try {
                            Map<String, Object> userFavPlacesMap = (Map<String, Object>) documentSnapshot.getData().get("userFavPlaces");
                            for (Map.Entry<String, Object> entry : userFavPlacesMap.entrySet()) {

                                Map<String, Object> favPlacesMap = (Map<String, Object>) entry.getValue();
                                String name = (String) favPlacesMap.get("nameOfFavPlace");
                                Timestamp curdate = (Timestamp) favPlacesMap.get("currentDate");
                                String date = curdate.toDate().toString();
                                String url = (String) favPlacesMap.get("imageUrl");
                                double latitude = (double) favPlacesMap.get("favPlaceLat");
                                double longitude = (double) favPlacesMap.get("favPlaceLon");
                                List<Address> addresses;
                                Geocoder geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
                                addresses = geocoder.getFromLocation(latitude, longitude, 1);

                                String address = addresses.get(0).getAddressLine(0);

                                if (!nameOfFavPlaces.contains(name) && !dateVisitedOfFavPlaces.contains(date) && !imageUrlOfFavPlaces.contains(url) &&
                                        !addressOfFavPlaces.contains(address)) {
                                    nameOfFavPlaces.add(name);
                                    dateVisitedOfFavPlaces.add(date);
                                    imageUrlOfFavPlaces.add(url);
                                    addressOfFavPlaces.add(address);
                                }
                            }
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                    } else {
                        Toast.makeText(FavouritePlaces.this, "You don't have any favourite places", Toast.LENGTH_SHORT).show();
                    }
                }
                MyAdapter myAdapter = new MyAdapter(nameOfFavPlaces, dateVisitedOfFavPlaces,imageUrlOfFavPlaces,addressOfFavPlaces,getApplicationContext());
                recyclerView.setAdapter(myAdapter);
                recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
                DividerItemDecoration dividerItemDecoration =
                        new DividerItemDecoration(getApplicationContext(),
                                DividerItemDecoration.VERTICAL);
                recyclerView.addItemDecoration(dividerItemDecoration);
            }
        });

    }
    public void takePhoto(View view){
        OpenDialogBox openDialogBox = new OpenDialogBox();
        openDialogBox.show(getSupportFragmentManager(), "Open Dialog Box");
    }



    @Override
    public void sendData(Uri uri, final String nameOfFavouritePlace, Date date, double lat, double lon) {
        if(uri!= null && nameOfFavouritePlace != null && date != null && lat != 0 && lon != 0) {
            imageUri = uri;
            nameOfPlace = nameOfFavouritePlace;
            currentDate = date;
            favPlaceLat = lat;
            favPlaceLon = lon;

            if(storageTask != null && storageTask.isInProgress()){
                Toast.makeText(this, "Upload in Progress", Toast.LENGTH_SHORT).show();
            } else {
                final String imageName = System.currentTimeMillis() +
                        "." + getFileExtension(imageUri);
                fileReference = storageReference.child(imageName);

                storageTask = fileReference.putFile(imageUri)
                        .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                Handler handler = new Handler();
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        progressBar.setProgress(0);
                                        fileReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                            @Override
                                            public void onSuccess(Uri uri) {
                                                Log.i("Info", uri.toString());
                                                imageUrl = uri.toString();
                                                if (imageUrl != null) {
                                                    saveDataInDatabase();
                                                    Toast.makeText(FavouritePlaces.this, "Data Added Successfully", Toast.LENGTH_SHORT).show();
                                                } else {
                                                    Toast.makeText(FavouritePlaces.this, "No Image Url Found", Toast.LENGTH_SHORT).show();
                                                }
                                            }
                                        });


                                    }
                                }, 500);

                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Toast.makeText(FavouritePlaces.this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                            }
                        })
                        .addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                            @Override
                            public void onProgress(@NonNull UploadTask.TaskSnapshot taskSnapshot) {
                                double progress = (100.0 * taskSnapshot.getBytesTransferred() / taskSnapshot.getTotalByteCount());
                                progressBar.setProgress((int) progress);
                            }
                        });
            }

        }
    }

    public void saveDataInDatabase(){

        documentReference.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                favPlaceCount = (long) documentSnapshot.get("favPlaceCount");

                Map<String, Object> usersData = new HashMap<>();
                Map<String, Object> favPlaceIds = new HashMap<>();
                Map<String, Object> favPlaces = new HashMap<>();

                favPlaces.put("favPlaceId", favPlaceCount);
                favPlaces.put("imageUrl", imageUrl);
                favPlaces.put("currentDate", currentDate);
                favPlaces.put("nameOfFavPlace", nameOfPlace);
                favPlaces.put("favPlaceLat", favPlaceLat);
                favPlaces.put("favPlaceLon", favPlaceLon);

                favPlaceIds.put(String.valueOf(favPlaceCount), favPlaces);

                long m = favPlaceCount + 1;

                usersData.put("userFavPlaces", favPlaceIds);
                usersData.put("favPlaceCount", m);


                if (userId != null) {
                    firebaseFirestore.collection("users")
                            .document(userId)
                            .set(usersData, SetOptions.merge())
                            .addOnCompleteListener(new OnCompleteListener<Void>() {
                                @Override
                                public void onComplete(@NonNull Task<Void> task) {
                                    Log.i("Info", "Fav Place Added");
                                }
                            })
                            .addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    e.printStackTrace();
                                }
                            });
                } else {
                    Toast.makeText(getApplicationContext(), "Error", Toast.LENGTH_SHORT).show();
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                e.printStackTrace();
            }
        });
    }

    public String getFileExtension(Uri uri){
        ContentResolver contentResolver = getContentResolver();
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        return mimeTypeMap.getExtensionFromMimeType(contentResolver.getType(uri));
    }

    public void goToMap(View view){
        Intent intent = new Intent(getApplicationContext(), OpenMapActivity.class);
        startActivity(intent);
    }

}