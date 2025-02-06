import React, { useState } from 'react';
import ImageUploader from './components/ImageUploader';
import './App.css';

function App() {
    const [processedImage, setProcessedImage] = useState(null);

    const handleImageProcessed = (image) => {
        setProcessedImage(image);
    };

    return (
        <div className="App vh-100">
            <h1>Passport Photo Generator</h1>
            <ImageUploader onImageProcessed={handleImageProcessed} />
            {processedImage && (
                <div>
                    <h2>Processed Image:</h2>
                    <img src={processedImage} alt="Processed" />
                </div>
            )}
        </div>
    );
}

export default App;
