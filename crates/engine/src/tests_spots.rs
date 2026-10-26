//! Remove tool commands: add (automatic source), select, move target/source, edit, refresh, delete.

use serde_json::json;

use crate::Session;

fn spots(s: &Session) -> Vec<lightcraft_develop::Spot> {
    s.develop_of(s.active().unwrap()).unwrap().spots.clone()
}

#[test]
fn spots_select_move_edit_refresh_and_delete() {
    let mut s = Session::with_demo();
    // a remove spot gets a resolved source (a pin to show) and is selected
    let r = s.execute("spot.add", &json!({"points": [[0.4, 0.5]], "size": 0.02})).unwrap();
    assert_eq!(r["index"], 0);
    assert_eq!(s.active_spot, Some(0));
    let src0 = spots(&s)[0].source_offset.expect("automatic source resolved");
    assert!(src0.x.hypot(src0.y) > 0.02, "the source is outside the spot: {src0:?}");
    s.execute("spot.add", &json!({"mode": "clone", "points": [[0.7, 0.3]], "source": [0.1, 0.0]})).unwrap();
    assert_eq!(s.active_spot, Some(1));
    assert_eq!(s.execute("library.state", &json!({})).unwrap()["activeSpot"], 1);
    // select
    s.execute("spot.select", &json!({"index": 0})).unwrap();
    assert_eq!(s.active_spot, Some(0));
    assert!(s.execute("spot.select", &json!({"index": 9})).is_err());
    // move the target (the source follows: its offset is relative), then the source alone
    s.execute("spot.update", &json!({"move": [0.05, -0.1]})).unwrap();
    let sp = &spots(&s)[0];
    assert!((sp.points[0].x - 0.45).abs() < 1e-9 && (sp.points[0].y - 0.4).abs() < 1e-9);
    assert_eq!(sp.source_offset, Some(src0));
    s.execute("spot.update", &json!({"moveSource": [0.01, 0.02]})).unwrap();
    let o = spots(&s)[0].source_offset.unwrap();
    assert!((o.x - src0.x - 0.01).abs() < 1e-9 && (o.y - src0.y - 0.02).abs() < 1e-9);
    // size / feather / opacity of the selected spot (clamped), one undo step each
    s.execute("spot.update", &json!({"size": 0.03, "feather": 20, "opacity": 150})).unwrap();
    let sp = &spots(&s)[0];
    assert_eq!((sp.size, sp.feather, sp.opacity), (0.03, 20.0, 100.0));
    s.execute("edit.undo", &json!({})).unwrap();
    assert_eq!(spots(&s)[0].size, 0.02);
    // refresh: a different source, at least a spot radius away
    let before = spots(&s)[0].source_offset.unwrap();
    let r = s.execute("spot.refreshSource", &json!({})).unwrap();
    let after = spots(&s)[0].source_offset.unwrap();
    assert!((after.x - before.x).hypot(after.y - before.y) > 0.01, "{r}");
    // delete the selected spot: the selection clears; deleting before a selected one shifts it
    s.execute("spot.delete", &json!({})).unwrap();
    assert_eq!((spots(&s).len(), s.active_spot), (1, None));
    assert_eq!(spots(&s)[0].mode, lightcraft_develop::SpotMode::Clone);
    assert!(s.execute("spot.delete", &json!({})).is_err(), "nothing selected");
    s.execute("spot.add", &json!({"points": [[0.2, 0.2]], "source": [0.05, 0.0]})).unwrap();
    s.execute("spot.delete", &json!({"index": 0})).unwrap();
    assert_eq!(s.active_spot, Some(0));
    // another photo: no selection
    s.execute("library.next", &json!({})).unwrap();
    assert_eq!(s.active_spot, None);
}

// Given a brushed area, aiInput writes the model's input; the model's fill (here, the input itself
// with the masked pixels painted flat) is stored as a patch by an AI spot, which renders, and which
// can't change mode or take a source
#[test]
fn ai_spots_store_the_models_fill_and_render_it() {
    let mut s = Session::with_demo();
    let dir = std::env::temp_dir().join(format!("lightcraft-ai-test-{}", std::process::id()));
    let pts = json!([[0.45, 0.5], [0.5, 0.5]]);
    let a = s.execute("spot.aiInput", &json!({"points": pts, "size": 0.03, "maxEdge": 256, "dir": dir.to_string_lossy()})).unwrap();
    let (w, h) = (a["width"].as_u64().unwrap() as usize, a["height"].as_u64().unwrap() as usize);
    assert!(w % 8 == 0 && h % 8 == 0 && w.max(h) <= 256, "{a}");
    let mut rgb = std::fs::read(a["image"].as_str().unwrap()).unwrap();
    let mask = std::fs::read(a["mask"].as_str().unwrap()).unwrap();
    assert_eq!((rgb.len(), mask.len()), (w * h * 3, w * h));
    assert!(mask.iter().any(|m| *m == 255) && mask.iter().any(|m| *m == 0));
    for (i, m) in mask.iter().enumerate() {
        if *m == 255 {
            rgb[i * 3..i * 3 + 3].copy_from_slice(&[120, 120, 120]);
        }
    }
    let fill = dir.join("ai-output.rgb");
    std::fs::write(&fill, &rgb).unwrap();
    let patch = json!({"file": fill.to_string_lossy(), "width": w, "height": h, "gain": a["gain"], "rect": a["rect"]});
    // nothing changed yet; an AI spot without its patch is refused
    assert!(spots(&s).is_empty());
    let id0 = s.active().unwrap();
    let plain = s.render_now(id0, 320, 240).unwrap().image;
    assert!(s.execute("spot.add", &json!({"mode": "ai", "points": pts, "size": 0.03})).is_err());
    let r = s.execute("spot.add", &json!({"mode": "ai", "points": pts, "size": 0.03, "patch": patch})).unwrap();
    assert_eq!(r["index"], 0);
    let sp = &spots(&s)[0];
    assert_eq!(sp.mode, lightcraft_develop::SpotMode::Ai);
    let id = sp.patch.as_ref().unwrap().id.clone();
    assert!(crate::cmd::patches_dir(&s).join(format!("{id}.lcpatch")).exists());
    assert!(lightcraft_pipeline::spots::patch(&id).is_some());
    // it renders: the photo changes under the spot, and only there (the top-left corner is as it was)
    let filled = s.render_now(id0, 320, 240).unwrap().image;
    assert_eq!((filled.width, filled.height), (plain.width, plain.height));
    assert_ne!(filled.data, plain.data);
    let corner = |i: &lightcraft_raster::Rgba8| i.data[..i.width * 10].to_vec();
    assert_eq!(corner(&filled), corner(&plain));
    assert!(s.execute("spot.update", &json!({"size": 0.04})).is_ok());
    assert!(s.execute("spot.update", &json!({"mode": "heal"})).is_err());
    assert!(s.execute("spot.update", &json!({"moveSource": [0.01, 0.0]})).is_err());
    assert!(s.execute("spot.refreshSource", &json!({})).is_err());
    // a plain spot can't become an AI one
    s.execute("spot.add", &json!({"points": [[0.2, 0.2]], "source": [0.05, 0.0]})).unwrap();
    assert!(s.execute("spot.update", &json!({"mode": "ai"})).is_err());
    let _ = std::fs::remove_dir_all(&dir);
}
