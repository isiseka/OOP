import React, { useState } from 'react';
import axios from 'axios';

function ImageUploader({ onImageProcessed }) {
    const [selectedImage, setSelectedImage] = useState(null);

    const handleImageChange = (event) => {
        setSelectedImage(event.target.files[0]);
    };

    const handleUpload = async () => {
        if (!selectedImage) {
            alert("Please select an image.");
            return;
        }

        const formData = new FormData();
        formData.append('image', selectedImage);

        try {
            const response = await axios.post('http://localhost:8080/api/image/process', formData, {
                headers: {
                    'Content-Type': 'multipart/form-data'
                },
                responseType: 'blob' // Important:  Tell Axios to expect a blob
            });

            const imageUrl = URL.createObjectURL(response.data);
            onImageProcessed(imageUrl);

        } catch (error) {
            console.error('Error processing image:', error);
            alert('Error processing image.');
        }
    };

    return (
        <div className=''>
            <input type="file" accept="image/*" onChange={handleImageChange} />
            <button onClick={handleUpload} disabled={!selectedImage}>Process Image</button>
        </div>
    );
}

export default ImageUploader;
