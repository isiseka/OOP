import React, { useState } from 'react';

const ImageUpload = () => {
  const [image, setImage] = useState(null);
  const [processedImage, setProcessedImage] = useState(null);

  const handleImageUpload = async (event) => {
    const file = event.target.files[0];
    const formData = new FormData();
    formData.append('image', file);

    // Send the image to the backend for processing
    try {
      const response = await fetch('http://localhost:8080/process-image', {
        method: 'POST',
        body: formData,
      });
      const data = await response.json();
      setProcessedImage(data.processedImage); // Assuming the backend returns a base64 image
    } catch (error) {
      console.error('Error processing image:', error);
    }
  };

  return (
    <div>
      <h1>Upload Your Image</h1>
      <input type="file" accept="image/*" onChange={handleImageUpload} />
      {image && <img src={URL.createObjectURL(image)} alt="Uploaded" />}
      {processedImage && (
        <div>
          <h2>Processed Image</h2>
          <img src={`data:image/png;base64,${processedImage}`} alt="Processed" />
        </div>
      )}
    </div>
  );
};

export default ImageUpload;