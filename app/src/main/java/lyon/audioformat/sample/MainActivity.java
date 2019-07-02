package lyon.audioformat.sample;

import androidx.appcompat.app.AppCompatActivity;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lyon.audioformat.sample.Permissions.PermissionsActivity;
import lyon.audioformat.sample.Permissions.PermissionsChecker;

//ref:
//https://juejin.im/post/5c3406ce6fb9a04a102f73dd


public class MainActivity extends AppCompatActivity {
    String TAG = MainActivity.class.getSimpleName();

    private static final boolean USE_VOICEHAT_I2S_DAC = Build.DEVICE.equals(BoardDefaults.DEVICE_RPI3);
    //record
    File mAudioFile;
    FileOutputStream mFileOutputStream;
    private static AudioRecord mAudioRecorder;
    // ????
    private byte[] mBuffer;


    TextView textView;

    private ExecutorService mExecutorService;

    long start;
    long end;

    private volatile boolean mIsRecording=true;
    private volatile boolean mIsPalying;

    Button playBtn,RecordBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 缺少权限时, 进入权限配置页面
        PermissionsChecker mPermissionsChecker = new PermissionsChecker(this);
        if (mPermissionsChecker.lacksPermissions(PermissionsActivity.PERMISSIONS)) {
            //Toast.makeText(getBaseContext(),"start ask!!",Toast.LENGTH_SHORT).show();
            PermissionsActivity.startActivityForResult(this, PermissionsActivity.REQUEST_CODE, PermissionsActivity.PERMISSIONS);
        } else {
            setContentView(R.layout.activity_main);

            textView = (TextView) findViewById(R.id.textViewStream);
            RecordBtn = (Button) findViewById(R.id.Record);
            playBtn = (Button) findViewById(R.id.play);

            mBuffer = new byte[2048];
            mExecutorService = Executors.newSingleThreadExecutor();

            RecordBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mIsRecording) {
                        RecordBtn.setText("Record");
                        mIsRecording = false;
                    } else {
                        RecordBtn.setText("Recording");
                        mIsRecording = true;
                        mExecutorService.submit(new Runnable() {
                            @Override
                            public void run() {
                                if (!startRecorder()) {
                                    recoderFail();
                                    try {
                                        RecordBtn.setText("Record");
                                    }catch (Exception e){
                                        Log.e(TAG,""+e);
                                    }
                                }
                            }
                        });
                    }
                }
            });

            playBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mIsRecording) {
                        RecordBtn.setText("Record");
                        mIsRecording = false;
                    }
                    streamPlay(view);

                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutorService.shutdownNow();
    }

//==================Record=============================================================================
    public void mkFile() {
        String path =Environment.getExternalStorageDirectory().getAbsolutePath() + "/RecorderTest/";
        mAudioFile = new File( path+ "Lyon.pcm");
        if (null != mAudioFile) {
            if (mAudioFile.exists()) {
                mAudioFile.delete();
            }
            mAudioFile.getParentFile().mkdirs();
            try {
                mAudioFile.createNewFile();
            } catch (IOException e) {
                Log.e(TAG, "IO Exception on creating audio file " + mAudioFile.toString(), e);
            }
        }
    }

    private boolean startRecorder() {
        Log.d(TAG,"startRecorder");
        try {
            mkFile();
            mFileOutputStream = new FileOutputStream(mAudioFile);

            int audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;
            int sampleRate = 44100;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            int mSampleRate = 16000;


            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

            mAudioRecorder = new AudioRecord(audioSource, sampleRate, channelConfig,
                    audioFormat, Math.max(minBufferSize, 2048));
            mAudioRecorder.startRecording();

            start = System.currentTimeMillis();

            while (mIsRecording) {
                int read = mAudioRecorder.read(mBuffer, 0, 2048);
                if (read > 0) {
                    mFileOutputStream.write(mBuffer, 0, read);
                } else {
                    return false;
                }
            }
            return stopRecorder();

        } catch (IOException | RuntimeException e) {
            Log.e(TAG,""+e);
            return false;
        } finally {
            if (mAudioRecorder != null) {
                mAudioRecorder.release();
                mAudioRecorder = null;
            }
        }
    }

    private void recoderFail() {
        mAudioFile = null;
        mIsRecording = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "record fail", Toast.LENGTH_SHORT).show();
            }
        });

    }


    private boolean stopRecorder() {
        Log.d(TAG,"stopRecorder");
        try {
            mAudioRecorder.stop();
            mAudioRecorder.release();
            mAudioRecorder = null;
            mFileOutputStream.close();

            end = System.currentTimeMillis();

            final int second = (int) ((end - start) / 1000);
            if (second > 3) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText(textView.getText() + "\nsuccess" + second + "S");
                    }
                });
            } else {
                mAudioFile.deleteOnExit();
            }
        } catch (IOException e) {
            Log.e(TAG,""+e);
            return false;
        }
        return true;
    }


//==================play=============================================================================
    public void streamPlay(View view) {
        if (mAudioFile != null && !mIsPalying) {
            mIsPalying = true;
            textView.setText(textView.getText() + "\n开始播放。。。");
            playBtn.setText("结束播放");
            mExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    doPaly(mAudioFile);
                }
            });
        }else{
            mIsPalying = false;
            playBtn.setText("播放");
            textView.setText(textView.getText() + "\n播放结束");
        }
    }

    private void doPaly(File mAudioFile) {
        int streamType = AudioManager.STREAM_MUSIC;
        int sampleRate = 44100;
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int mode = AudioTrack.MODE_STREAM;

        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);

        AudioTrack audioTrack = new AudioTrack(streamType, sampleRate, channelConfig, audioFormat,
                Math.max(minBufferSize, 2048), mode);

        FileInputStream mFileInputStream = null;
        try {
            mFileInputStream = new FileInputStream(mAudioFile);
            int read;
            audioTrack.play();
            while ((read = mFileInputStream.read(mBuffer)) > 0 &&mIsPalying) {
                int ret = audioTrack.write(mBuffer, 0, read);
                switch (ret) {
                    case AudioTrack.ERROR_BAD_VALUE:
                    case AudioTrack.ERROR_INVALID_OPERATION:
                    case AudioManager.ERROR_DEAD_OBJECT:
                        palyFaile();
                        break;
                    default:
                        break;
                }
            }
        } catch (RuntimeException | IOException e) {
            e.printStackTrace();
            palyFaile();
        } finally {
            mIsPalying = false;
            if (mFileInputStream != null) {
                closeQuietly(mFileInputStream);
            }
            audioTrack.stop();
            audioTrack.release();
            playBtn.setText("播放");
            textView.setText(textView.getText() + "\n播放结束");
        }
    }

    private void closeQuietly(FileInputStream mFileInputStream) {
        try {
            mFileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void palyFaile() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "????", Toast.LENGTH_SHORT).show();
            }
        });
    }
}






